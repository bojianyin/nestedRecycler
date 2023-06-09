package com.xb.nestedrecycler
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xb.nestedrecycler.adapter.BaseMultiTypeAdapter
import com.xb.nestedrecycler.utils.FlingHelper
import com.xb.nestedrecycler.utils.UIUtils
import java.util.concurrent.atomic.AtomicBoolean


class ParentRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    RecyclerView(context, attrs, defStyleAttr) {

    private var mMaxDistance:Int = 0

    private val mFlingHelper = FlingHelper(context)
    /**
     * 记录上次Event事件的y坐标
     */
    private var lastY:Float = 0f

    var totalDy = 0
    /**
     * 用于判断RecyclerView是否在fling
     */
    var isStartFling =  false
    /**
     * 记录当前滑动的y轴加速度
     */
    private var velocityY: Int = 0

    var canScrollVertically: AtomicBoolean

    init {
        mMaxDistance = mFlingHelper.getVelocityByDistance((UIUtils.getScreenHeight() * 4).toDouble())

        canScrollVertically = AtomicBoolean(true)
        addOnScrollListener(object :OnScrollListener(){
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                //如果父RecyclerView fling过程中已经到底部，需要让子RecyclerView滑动神域的fling
                if(newState == RecyclerView.SCROLL_STATE_IDLE) {
                    dispatchChildFling()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if(isStartFling) {
                    totalDy = 0
                    isStartFling = false
                }
                //在RecyclerView fling情况下，记录当前RecyclerView在y轴的偏移
                totalDy += dy
            }
        })
    }

    private fun dispatchChildFling() {
        if (isScrollEnd() && velocityY != 0) {
            val splineFlingDistance = mFlingHelper.getSplineFlingDistance(velocityY)
            if (splineFlingDistance > totalDy) {
                childFling(mFlingHelper.getVelocityByDistance(splineFlingDistance - totalDy.toDouble()))
            }
        }
        totalDy = 0
        velocityY = 0
    }

    private fun childFling(velY: Int) {
        findNestedScrollingChildRecyclerView()?.fling(0,velY)
    }

    fun initLayoutManager() {
        val linearLayoutManager = object : LinearLayoutManager(context) {
            override fun scrollVerticallyBy(dy: Int, recycler: Recycler?, state: State?): Int {
                return try {
                    super.scrollVerticallyBy(dy, recycler, state)
                } catch (e: Exception) {
                    e.printStackTrace()
                    0
                }
            }

            override fun onLayoutChildren(recycler: Recycler?, state: State?) {
                try {
                    super.onLayoutChildren(recycler, state)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun canScrollVertically(): Boolean {
                val childRecyclerView = findNestedScrollingChildRecyclerView()
                return canScrollVertically.get() || childRecyclerView == null || childRecyclerView.isScrollTop()

            }

            override fun addDisappearingView(child: View?) {
                try {
                    super.addDisappearingView(child)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun supportsPredictiveItemAnimations(): Boolean {
                return false
            }
        }
        linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
        layoutManager = linearLayoutManager
    }


    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if(ev != null && ev.action == MotionEvent.ACTION_DOWN) {
            //ACTION_DOWN的时候重置加速度
            velocityY = 0
            stopScroll()
        }
        if((ev == null || ev.action == MotionEvent.ACTION_MOVE).not()) {
            //在非ACTION_MOVE的情况下，将lastY置为0
            lastY = 0f
        }
        return try {
            super.dispatchTouchEvent(ev)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(lastY == 0f) {
            lastY = e.y
        }
        if(isScrollEnd()) {
            //如果父RecyclerView已经滑动到底部，需要让子RecyclerView滑动剩余的距离

            val childRecyclerView = findNestedScrollingChildRecyclerView()
            childRecyclerView?.run {
                val deltaY = (lastY - e.y).toInt()

                canScrollVertically.set(false)
                scrollBy(0,deltaY)
            }
        }
        if(e.action == MotionEvent.ACTION_UP) {
            canScrollVertically.set(true)
        }
        lastY = e.y
        return try {
            super.onTouchEvent(e)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun fling(velX: Int, velY: Int): Boolean {
        val fling = super.fling(velX, velY)
        if (!fling || velY <= 0) {
            velocityY = 0
        } else {
            isStartFling = true
            velocityY = velY
        }
        return fling
    }

    private fun isScrollEnd(): Boolean {
        //RecyclerView.canScrollVertically(1)的值表示是否能向上滚动，false表示已经滚动到底部
        return !canScrollVertically(1)
    }

    private fun findNestedScrollingChildRecyclerView(): ChildRecyclerView? {
        (adapter as? BaseMultiTypeAdapter)?.apply {
            return getCurrentChildRecyclerView()
        }
        return null
    }


    override fun scrollToPosition(position: Int) {
        //处理一键置顶会出现卡顿的问题
        findNestedScrollingChildRecyclerView()?.scrollToPosition(position)
        postDelayed({
            super.scrollToPosition(position)
        },50)
    }

    //----------------------------------------------------------------------------------------------
    // NestedScroll. fix：当ChildRecyclerView下滑时(手指未放开)，ChildRecyclerView滑动到顶部（非fling），此时ParentRecyclerView不会继续下滑。
    //----------------------------------------------------------------------------------------------

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return (target != null) && target is ChildRecyclerView
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        val childRecyclerView = findNestedScrollingChildRecyclerView()
        //1.当前Parent RecyclerView没有滑动底，且dy> 0 是下滑
        val isParentCanScroll = dy > 0 && isScrollEnd().not()
        //2.当前Child RecyclerView滑到顶部了，且dy < 0,即上滑
        val isChildCanNotScroll = !(dy >= 0
                || childRecyclerView == null
                || childRecyclerView.isScrollTop().not())
        //以上两种情况都需要让Parent RecyclerView去scroll，和下面onNestedPreFling机制类似
        if(isParentCanScroll || isChildCanNotScroll) {
            scrollBy(0,dy)
            consumed[1] = dy
        }
    }

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return true
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        val childRecyclerView = findNestedScrollingChildRecyclerView()
        val isParentCanFling = velocityY > 0f && isScrollEnd().not()
        val isChildCanNotFling = !(velocityY >= 0
                || childRecyclerView == null
                || childRecyclerView.isScrollTop().not())
        if(isParentCanFling.not() && isChildCanNotFling.not()) {
            return false
        }
        fling(0,velocityY.toInt())
        return true
    }

    fun isChildRecyclerViewCanScrollUp(): Boolean {
        return findNestedScrollingChildRecyclerView()?.isScrollTop()?.not() ?: false
    }

    //----------------------------------------------------------------------------------------------
    // NestedScroll. fix：当ChildRecyclerView下滑时(手指未放开)，ChildRecyclerView滑动到顶部（非fling），此时ParentRecyclerView不会继续下滑。
    //----------------------------------------------------------------------------------------------



    private var oldX = 0F
    private var oldY = 0F

    private var newX = 0F
    private var newY = 0F

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        val intercept = super.onInterceptTouchEvent(e)
        if (e != null) {
            val action = e.action
            when (action) {
                MotionEvent.ACTION_DOWN -> run {
                    oldX = e.x
                    oldY = e.y
                    Log.d("dispatchTouchEvent", "parent ACTION_DOWN")
                }
                MotionEvent.ACTION_MOVE -> run {
                    newX = e.x
                    newY = e.y
                    Log.d(
                        "onInterceptTouchEvent",
                        "onInterceptTouchEvent parent oldX:" + oldX + ",oldY:" + oldY
                    )
                    Log.d(
                        "onInterceptTouchEvent",
                        "onInterceptTouchEvent parent newX:" + newX + ",newY:" + newY
                    )
                    oldX = newX
                    oldY = newY
                }
                MotionEvent.ACTION_CANCEL -> run {
                    Log.d("dispatchTouchEvent", "parent ACTION_CANCEL")
                }
                MotionEvent.ACTION_UP -> run {
                    Log.d("dispatchTouchEvent", "parent ACTION_UP")
                }
            }
        }
        val childRecyclerView: RecyclerView? = findNestedScrollingChildRecyclerView()
        if (childRecyclerView != null) {
            val isScrollUp = !childRecyclerView.canScrollVertically(-1)
            if (isScrollUp) {
                Log.d("dispatchTouchEvent", "parent 子滑动到顶部")
            } else {
                Log.d("dispatchTouchEvent", "parent 子没有滑动到顶部，可以继续向上滑动")
                /*
                不再这里去处理手势了，放在子中，不用去查询view，提高效率
                如果x轴偏移量大于y轴此时父控件先去处理，之后再交给子控件
                 */
                return if (Math.abs(newX - oldX) > Math.abs(newY - oldY)) {
                    intercept
                } else {
                    false
                }
            }
        }
        if (intercept) {
            Log.d("dispatchTouchEvent", "parent 父控件拦截手势了")
        } else {
            Log.d("dispatchTouchEvent", "parent 父控件未拦截手势")
        }
        return intercept
    }
}