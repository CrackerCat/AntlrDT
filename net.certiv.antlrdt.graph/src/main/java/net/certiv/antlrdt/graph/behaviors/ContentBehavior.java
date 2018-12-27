/*******************************************************************************
 * Copyright (c) 2014, 2017 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *
 * Note: Parts of this class have been transferred from org.eclipse.gef.editparts.AbstractEditPart.
 *******************************************************************************/
package net.certiv.antlrdt.graph.behaviors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javafx.beans.property.ReadOnlyProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.scene.Node;

import org.eclipse.gef.common.collections.SetMultimapChangeListener;
import org.eclipse.gef.common.dispose.IDisposable;
import org.eclipse.gef.mvc.fx.behaviors.AbstractBehavior;
import org.eclipse.gef.mvc.fx.behaviors.ContentPartPool;
import org.eclipse.gef.mvc.fx.parts.IContentPart;
import org.eclipse.gef.mvc.fx.parts.IContentPartFactory;
import org.eclipse.gef.mvc.fx.parts.IVisualPart;
import org.eclipse.gef.mvc.fx.parts.PartUtils;
import org.eclipse.gef.mvc.fx.viewer.IViewer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;

/**
 * A behavior that can be adapted to an {@link IRootPart} or an {@link IContentPart} to synchronize
 * the list of {@link IContentPart} children and (only in case of an {@link IContentPart})
 * anchorages with the list of content children and anchored.
 */
public class ContentBehavior extends AbstractBehavior implements IDisposable {

	private ListChangeListener<Object> contentObserver = new ListChangeListener<Object>() {
		@Override
		public void onChanged(ListChangeListener.Change<? extends Object> change) {
			synchronizeContentPartChildren(getHost(), change.getList());
		}
	};

	private ListChangeListener<Object> contentChildrenObserver = new ListChangeListener<Object>() {
		@SuppressWarnings("unchecked")
		@Override
		public void onChanged(final ListChangeListener.Change<? extends Object> change) {
			IContentPart<? extends Node> parent = (IContentPart<? extends Node>) ((ReadOnlyProperty<?>) change
					.getList()).getBean();
			synchronizeContentPartChildren(parent, change.getList());
		}
	};

	private SetMultimapChangeListener<Object, String> contentAnchoragesObserver = new SetMultimapChangeListener<Object, String>() {
		@SuppressWarnings("unchecked")
		@Override
		public void onChanged(final SetMultimapChangeListener.Change<? extends Object, ? extends String> change) {
			IContentPart<? extends Node> anchored = (IContentPart<? extends Node>) ((ReadOnlyProperty<?>) change
					.getSetMultimap()).getBean();
			synchronizeContentPartAnchorages(anchored, HashMultimap.create(change.getSetMultimap()));
		}
	};

	private MapChangeListener<Object, IContentPart<? extends Node>> contentPartMapObserver = new MapChangeListener<Object, IContentPart<? extends Node>>() {
		@Override
		public void onChanged(
				MapChangeListener.Change<? extends Object, ? extends IContentPart<? extends Node>> change) {
			if (change.wasRemoved()) {
				IContentPart<? extends Node> contentPart = change.getValueRemoved();
				contentPart.contentChildrenUnmodifiableProperty().removeListener(contentChildrenObserver);
				contentPart.contentAnchoragesUnmodifiableProperty().removeListener(contentAnchoragesObserver);
			}
			if (change.wasAdded()) {
				IContentPart<? extends Node> contentPart = change.getValueAdded();
				contentPart.contentChildrenUnmodifiableProperty().addListener(contentChildrenObserver);
				contentPart.contentAnchoragesUnmodifiableProperty().addListener(contentAnchoragesObserver);
			}
		}
	};

	@Override
	protected void doActivate() {
		IVisualPart<? extends Node> host = getHost();
		if (host != host.getRoot()) {
			throw new IllegalArgumentException();
		}
		IViewer viewer = host.getRoot().getViewer();
		viewer.contentPartMapProperty().addListener(contentPartMapObserver);
		synchronizeContentPartChildren(getHost(), viewer.getContents());
		viewer.getContents().addListener(contentObserver);
	}

	@Override
	protected void doDeactivate() {
		IVisualPart<? extends Node> host = getHost();
		IViewer viewer = host.getRoot().getViewer();
		viewer.getContents().removeListener(contentObserver);
		synchronizeContentPartChildren(getHost(), Collections.emptyList());
		viewer.contentPartMapProperty().removeListener(contentPartMapObserver);
	}

	/**
	 * Finds/Revives/Creates an {@link IContentPart} for the given <i>content</i> {@link Object}. If an
	 * {@link IContentPart} for the given content {@link Object} can be found in the viewer's
	 * content-part-map, then this part is returned. If an {@link IContentPart} for the given content
	 * {@link Object} is stored in the {@link ContentPartPool}, then this part is returned. Otherwise,
	 * the injected {@link IContentPartFactory} is used to create a new {@link IContentPart} for the
	 * given content {@link Object}.
	 *
	 * @param content The content {@link Object} for which the corresponding {@link IContentPart} is to
	 *            be returned.
	 * @return The {@link IContentPart} corresponding to the given <i>content</i> {@link Object}.
	 */
	protected IContentPart<? extends Node> findOrCreatePartFor(Object content) {
		Map<Object, IContentPart<? extends Node>> contentPartMap = getHost().getRoot().getViewer().getContentPartMap();
		if (contentPartMap.containsKey(content)) {
			// System.out.println("FOUND " + content);
			return contentPartMap.get(content);
		} else {
			// 'Revive' a content part, if it was removed before
			IContentPart<? extends Node> contentPart = getContentPartPool().remove(content);
			// If the part could not be revived, a new one is created
			if (contentPart == null) {
				// create part using the factory
				IContentPartFactory contentPartFactory = getContentPartFactory();
				contentPart = contentPartFactory.createContentPart(content, Collections.emptyMap());
				if (contentPart == null) {
					throw new IllegalStateException(
							"IContentPartFactory '" + contentPartFactory.getClass().getSimpleName()
									+ "' did not create part for " + content + ".");
				}
			}

			// initialize part
			contentPart.setContent(content);
			return contentPart;
		}
	}

	/**
	 * Returns the {@link IContentPartFactory} of the current viewer.
	 *
	 * @return the {@link IContentPartFactory} of the current viewer.
	 */
	protected IContentPartFactory getContentPartFactory() {
		return getHost().getRoot().getViewer().getAdapter(IContentPartFactory.class);
	}

	/**
	 * Returns the {@link ContentPartPool} that is used to recycle content parts in the context of an
	 * {@link IViewer}.
	 *
	 * @return The {@link ContentPartPool} of the {@link IViewer}.
	 */
	protected ContentPartPool getContentPartPool() {
		return getHost().getRoot().getViewer().getAdapter(ContentPartPool.class);
	}

	/**
	 * Updates the host {@link IVisualPart}'s {@link IContentPart} children (see
	 * {@link IVisualPart#getChildrenUnmodifiable()}) so that it is in sync with the set of content
	 * children that is passed in.
	 *
	 * @param parent The parent {@link IVisualPart} whose content part children to synchronize against
	 *            the given content children.
	 * @param contentChildren The list of content part children to be synchronized with the list of
	 *            {@link IContentPart} children ({@link IContentPart#getChildrenUnmodifiable()}).
	 */
	public void synchronizeContentPartChildren(IVisualPart<? extends Node> parent,
			final List<? extends Object> contentChildren) {

		if (contentChildren == null) {
			throw new IllegalArgumentException("contentChildren may not be null");
		}

		List<IContentPart<? extends Node>> toRemove = detachAll(parent, contentChildren);
		for (IContentPart<? extends Node> contentPart : toRemove) {
			if (contentPart.getParent() != null) contentPart.getParent().removeChild(contentPart);
			disposeIfObsolete(contentPart);
		}

		List<IContentPart<? extends Node>> added = addAll(parent, contentChildren);
		for (IContentPart<? extends Node> cp : added) {
			synchronizeContentPartAnchorages(cp, cp.getContentAnchoragesUnmodifiable());
		}
	}

	/**
	 * Updates the host {@link IVisualPart}'s {@link IContentPart} anchorages (see
	 * {@link IVisualPart#getAnchoragesUnmodifiable()}) so that it is in sync with the set of content
	 * anchorages that is passed in.
	 *
	 * @param anchored The anchored {@link IVisualPart} whose content part anchorages to synchronize
	 *            with the given content anchorages.
	 * @param contentAnchorages * The map of content anchorages with roles to be synchronized with the
	 *            list of {@link IContentPart} anchorages (
	 *            {@link IContentPart#getAnchoragesUnmodifiable()}).
	 * @see IContentPart#getContentAnchoragesUnmodifiable()
	 * @see IContentPart#getAnchoragesUnmodifiable()
	 */
	public void synchronizeContentPartAnchorages(IVisualPart<? extends Node> anchored,
			SetMultimap<? extends Object, ? extends String> contentAnchorages) {
		if (contentAnchorages == null) {
			throw new IllegalArgumentException("contentAnchorages may not be null");
		}
		SetMultimap<IVisualPart<? extends Node>, String> anchorages = anchored.getAnchoragesUnmodifiable();

		// find anchorages whose content vanished
		List<Entry<IVisualPart<? extends Node>, String>> toRemove = new ArrayList<>();
		Set<Entry<IVisualPart<? extends Node>, String>> entries = anchorages.entries();
		for (Entry<IVisualPart<? extends Node>, String> e : entries) {
			if (!(e.getKey() instanceof IContentPart)) {
				continue;
			}
			Object content = ((IContentPart<? extends Node>) e.getKey()).getContent();
			if (!contentAnchorages.containsEntry(content, e.getValue())) {
				toRemove.add(e);
			}
		}

		// Correspondingly remove the anchorages. This is done in a separate
		// step to prevent ConcurrentModificationException.
		for (Entry<IVisualPart<? extends Node>, String> contentPart : toRemove) {
			anchored.detachFromAnchorage(contentPart.getKey(), contentPart.getValue());
			disposeIfObsolete((IContentPart<? extends Node>) contentPart.getKey());
		}

		// find content for which no anchorages exist
		List<Entry<IVisualPart<? extends Node>, String>> toAdd = new ArrayList<>();
		for (Entry<? extends Object, ? extends String> e : contentAnchorages.entries()) {
			IContentPart<? extends Node> anchorage = findOrCreatePartFor(e.getKey());
			if (!anchorages.containsEntry(anchorage, e.getValue())) {
				toAdd.add(Maps.<IVisualPart<? extends Node>, String>immutableEntry(anchorage, e.getValue()));
			}
		}

		// Correspondingly add the anchorages. This is done in a separate
		// step to prevent ConcurrentModificationException.
		for (Entry<IVisualPart<? extends Node>, String> e : toAdd) {
			anchored.attachToAnchorage(e.getKey(), e.getValue());
		}
	}

	@SuppressWarnings("unchecked")
	private List<IContentPart<? extends Node>> addAll(IVisualPart<? extends Node> parent,
			List<? extends Object> contentChildren) {
		List<IContentPart<? extends Node>> childContentParts = PartUtils.filterParts(parent.getChildrenUnmodifiable(),
				IContentPart.class);
		List<IContentPart<? extends Node>> added = new ArrayList<>();
		// store the existing content parts in a map using the contents as keys
		Map<Object, IContentPart<? extends Node>> contentPartMap = new HashMap<>();
		// find all content parts for which no content element exists in
		// contentChildren, and therefore have to be removed
		for (IContentPart<? extends Node> contentPart : childContentParts) {
			// store content part in map
			contentPartMap.put(contentPart.getContent(), contentPart);
		}
		int contentChildrenSize = contentChildren.size();
		int childContentPartsSize = childContentParts.size();
		for (int i = 0; i < contentChildrenSize; i++) {
			Object content = contentChildren.get(i);
			// Do a quick check to see if the existing content part is at
			// the correct location in the children list.
			if (i < childContentPartsSize && childContentParts.get(i).getContent() == content) {
				continue;
			}
			// Look to see if the ContentPart is already around but in the
			// wrong location.
			IContentPart<? extends Node> contentPart = findOrCreatePartFor(content);
			if (contentPartMap.containsKey(content)) {
				// Re-order the existing content part to its designated
				// location in the children list.
				// TODO: this is wrong, it has to take into consideration
				// the visual parts in between
				parent.reorderChild(contentPart, i);
			} else {
				// A ContentPart for this model does not exist yet. Create
				// and insert one.
				if (contentPart.getParent() != null) {
					throw new IllegalStateException(
							"Located a ContentPart which controls the same (or an equal) content element but is already bound to a parent. A content element may only be controlled by a single ContentPart.");
				}
				parent.addChild(contentPart, i);
				added.add(contentPart);
				added.addAll(addAll(contentPart, contentPart.getContentChildrenUnmodifiable()));
			}
		}
		return added;
	}

	/**
	 * If the given {@link IContentPart} does neither have a parent nor any anchoreds, then it's content
	 * is set to <code>null</code> and the part is added to the {@link ContentPartPool}.
	 *
	 * @param contentPart The {@link IContentPart} that is eventually disposed.
	 */
	protected void disposeIfObsolete(IContentPart<? extends Node> contentPart) {
		if (contentPart.getParent() == null && contentPart.getAnchoredsUnmodifiable().isEmpty()) {
			getContentPartPool().add(contentPart);
			contentPart.setContent(null);
		}
	}

	@SuppressWarnings("unchecked")
	private List<IContentPart<? extends Node>> detachAll(IVisualPart<? extends Node> parent,
			final List<? extends Object> contentChildren) {
		List<IContentPart<? extends Node>> toRemove = new ArrayList<>();
		// only synchronize IContentPart children
		// find all content parts for which no content element exists in
		// contentChildren, and therefore have to be removed
		for (IContentPart<? extends Node> contentPart : (List<IContentPart<? extends Node>>) PartUtils
				.filterParts(parent.getChildrenUnmodifiable(), IContentPart.class)) {
			// mark for removal
			if (!contentChildren.contains(contentPart.getContent())) {
				toRemove.addAll(detachAll(contentPart, Collections.emptyList()));
				toRemove.add(contentPart);
				synchronizeContentPartAnchorages(contentPart, HashMultimap.create());
			}
		}
		return toRemove;
	}

	@Override
	public void dispose() {
		// the content part pool is shared by all content behaviors of a viewer,
		// so the viewer disposes it.
		contentObserver = null;
		contentChildrenObserver = null;
		contentAnchoragesObserver = null;
	}
}
