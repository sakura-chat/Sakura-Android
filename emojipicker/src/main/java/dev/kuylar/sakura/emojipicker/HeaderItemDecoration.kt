package dev.kuylar.sakura.emojipicker

/*
solution based on - based on Sevastyan answer on StackOverflow


changes:
- take to account views offsets
- transformed to Kotlin
- now works on viewHolders
- try to cache viewHolders between draw's
- support for clipToPadding=false

Source:
https://stackoverflow.com/questions/32949971/how-can-i-make-sticky-headers-in-recyclerview-without-external-lib/44327350#44327350
*/

import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.withSave
import androidx.recyclerview.widget.RecyclerView
import dev.kuylar.sakura.emojipicker.model.CategoryModel

class HeaderItemDecoration(
	private val parent: RecyclerView,
	private val shouldFadeOutHeader: Boolean = false,
	private val isHeader: (itemPosition: Int) -> Boolean,
	private val onHeaderChange: (CategoryModel) -> Unit
) : RecyclerView.ItemDecoration() {
	private var currentHeader: Pair<Int, RecyclerView.ViewHolder>? = null
	private var lastTopChild = -1
	private var adapter = parent.adapter as EmojiRecyclerAdapter

	init {
		adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
			override fun onChanged() {
				// clear saved header as it can be outdated now
				currentHeader = null
			}
		})

		parent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			// clear saved layout as it may need layout update
			currentHeader = null
		}
		// handle click on sticky header
		parent.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
			override fun onInterceptTouchEvent(
				recyclerView: RecyclerView,
				motionEvent: MotionEvent
			): Boolean {
				return if (motionEvent.action == MotionEvent.ACTION_DOWN) {
					motionEvent.y <= (currentHeader?.second?.itemView?.bottom ?: 0)
				} else false
			}
		})
	}

	override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
		super.onDrawOver(c, parent, state)
		//val topChild = parent.getChildAt(0) ?: return
		val topChild = parent.findChildViewUnder(
			parent.paddingLeft.toFloat(),
			parent.paddingTop.toFloat() /*+ (currentHeader?.second?.itemView?.height ?: 0 )*/
		) ?: return
		val topChildPosition = parent.getChildAdapterPosition(topChild)
		if (topChildPosition == RecyclerView.NO_POSITION) {
			return
		}

		val header = getHeaderViewForItem(topChildPosition, parent) ?: return
		val headerViewHolder = header.second ?: return
		val headerView = headerViewHolder.itemView
		val headerIndex = header.first

		val contactPoint = headerView.bottom + parent.paddingTop
		val childInContact = getChildInContact(parent, contactPoint) ?: return

		if (isHeader(parent.getChildAdapterPosition(childInContact))) {
			moveHeader(c, headerView, childInContact, parent.paddingTop)
			return
		}

		if (parent.paddingTop == 0 && lastTopChild != headerIndex) {
			lastTopChild = headerIndex
			onHeaderChange.invoke(adapter.getItemAt(headerIndex) as CategoryModel)
		}
		drawHeader(c, headerView, parent.paddingTop)
	}

	private fun getHeaderViewForItem(itemPosition: Int, parent: RecyclerView): Pair<Int, RecyclerView.ViewHolder?>? {
		if (parent.adapter == null) {
			return null
		}
		val headerPosition = getHeaderPositionForItem(itemPosition)
		if (headerPosition == RecyclerView.NO_POSITION) return null
		val headerType = adapter.getItemViewType(headerPosition)
		// if match reuse viewHolder
		if (currentHeader?.first == headerPosition && currentHeader?.second?.itemViewType == headerType) {
			return Pair(headerPosition, currentHeader?.second)
		}

		val headerHolder = adapter.createViewHolder(parent, headerType)
		adapter.onBindViewHolder(headerHolder, headerPosition)
		fixLayoutSize(parent, headerHolder.itemView)
		// save for next draw
		currentHeader = headerPosition to headerHolder
		return Pair(headerPosition, headerHolder)
	}

	private fun drawHeader(c: Canvas, header: View, paddingTop: Int) {
		c.withSave {
			translate(parent.paddingLeft.toFloat(), paddingTop.toFloat())
			header.draw(c)
			restore()
		}
	}

	private fun moveHeader(c: Canvas, currentHeader: View, nextHeader: View, paddingTop: Int) {
		c.withSave {
			if (!shouldFadeOutHeader) {
				clipRect(parent.paddingLeft, paddingTop, width, paddingTop + currentHeader.height)
			} else {
				saveLayerAlpha(
					RectF(parent.paddingLeft.toFloat(), 0f, width.toFloat(), height.toFloat()),
					(((nextHeader.top - paddingTop) / nextHeader.height.toFloat()) * 255).toInt()
				)

			}
			translate(
				parent.paddingLeft.toFloat(),
				(nextHeader.top - currentHeader.height).toFloat() /*+ paddingTop*/
			)

			currentHeader.draw(this)
			if (shouldFadeOutHeader) {
				restore()
			}
		}
	}

	private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
		var childInContact: View? = null
		for (i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			val mBounds = Rect()
			parent.getDecoratedBoundsWithMargins(child, mBounds)
			if (mBounds.bottom > contactPoint) {
				if (mBounds.top <= contactPoint) {
					// This child overlaps the contactPoint
					childInContact = child
					break
				}
			}
		}
		return childInContact
	}

	/**
	 * Properly measures and layouts the top sticky header.
	 *
	 * @param parent ViewGroup: RecyclerView in this case.
	 */
	private fun fixLayoutSize(parent: ViewGroup, view: View) {

		// Specs for parent (RecyclerView)
		val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
		val heightSpec =
			View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

		// Specs for children (headers)
		val childWidthSpec = ViewGroup.getChildMeasureSpec(
			widthSpec,
			parent.paddingLeft + parent.paddingRight,
			view.layoutParams.width
		)
		val childHeightSpec = ViewGroup.getChildMeasureSpec(
			heightSpec,
			parent.paddingTop + parent.paddingBottom,
			view.layoutParams.height
		)

		view.measure(childWidthSpec, childHeightSpec)
		view.layout(0, 0, view.measuredWidth, view.measuredHeight)
	}

	private fun getHeaderPositionForItem(itemPosition: Int): Int {
		var headerPosition = RecyclerView.NO_POSITION
		var currentPosition = itemPosition
		do {
			if (isHeader(currentPosition)) {
				headerPosition = currentPosition
				break
			}
			currentPosition -= 1
		} while (currentPosition >= 0)
		return headerPosition
	}
}