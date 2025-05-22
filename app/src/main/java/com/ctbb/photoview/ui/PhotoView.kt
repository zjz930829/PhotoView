package com.ctbb.photoview.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import androidx.core.animation.doOnEnd
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import com.ctbb.photoview.dps
import com.ctbb.photoview.getJayZhou
import kotlinx.coroutines.Runnable
import kotlin.math.max
import kotlin.math.min

/**
 * Created by CTBB on 2025-05-21.
 * Describe:
 */

private val IMAGE_SIZE: Int = 300.dps.toInt()
private val IMAGE_HEIGHT: Int = IMAGE_SIZE * 768 / 1024
private const val EXTRA_SCALE_FACTOR = 1.5f

class PhotoView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val bitmap = getJayZhou(resources, IMAGE_SIZE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var originalOffsetX = 0f
    private var originalOffsetY = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var smallScale = 0f
    private var bigScale = 0f
    private val photoGestureListener = PhotoGestureListener()
    private val photoScaleGestureListener = PhotoScaleGestureListener()
    private val photoFlingRunnable = PhotoFlingRunnable()
    private val gestureDetector = GestureDetectorCompat(context, photoGestureListener)
    private val scaleGestureDetector = ScaleGestureDetector(context, photoScaleGestureListener)

    private var big = false
    private var currentScale = 0f
        set(value) {
            field = value
            invalidate()
        }
//    private lateinit var scaleAnimator:ObjectAnimator
    //以下动画属性中smallScale和bigScale是会动态变化的，所以每次使用后都要重置
    private val scaleAnimator: ObjectAnimator =
        ObjectAnimator.ofFloat(this, "currentScale", smallScale, bigScale)

//    private fun getScaleAnimator():ObjectAnimator{
//        scaleAnimator = ObjectAnimator.ofFloat(this, "currentScale", smallScale, bigScale)
//        if (big){
//            scaleAnimator.setFloatValues(smallScale,currentScale)
//        }else{
//            scaleAnimator.setFloatValues(currentScale,bigScale)
//        }
//        return scaleAnimator
//    }

    //用OverScroller并非Scroller，
    private val scroller = OverScroller(context)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        originalOffsetX = (width - IMAGE_SIZE) / 2f
        originalOffsetY = (height - IMAGE_HEIGHT) / 2f
        if (bitmap.width / bitmap.height.toFloat() > width / height.toFloat()) {
            smallScale = width / bitmap.width.toFloat()
            bigScale = height / bitmap.height.toFloat() * EXTRA_SCALE_FACTOR
        } else {
            smallScale = height / bitmap.height.toFloat()
            bigScale = width / bitmap.width.toFloat() * EXTRA_SCALE_FACTOR
        }
        //初始化
        currentScale = smallScale
        scaleAnimator.setFloatValues(smallScale, bigScale)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        //当某一个事件来临之前，2个事件都同时工作，当某个抢夺性事件发生之后，只执行其中一个事件，另一个事件停止工作
        //如何定义抢夺性，当一个有歧义的事件不再有歧义的时候，比如点击就有歧义，但是双手缩放不会有歧义，所以双手缩放是抢夺性事件
        scaleGestureDetector.onTouchEvent(event)
        if (!scaleGestureDetector.isInProgress) {
            //当双手缩放事件不在执行，才执行手势事件
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        //缩放完成度百分比：分子[当前值-初始值]，分母[最大值-初始值]
        val scaleFraction = (currentScale - smallScale) / (bigScale - smallScale)
        canvas.translate(offsetX * scaleFraction, offsetY * scaleFraction)
//        val scale = smallScale + (bigScale - smallScale) * scaleFraction
        canvas.scale(currentScale, currentScale, width / 2f, height / 2f)
        canvas.drawBitmap(bitmap, originalOffsetX, originalOffsetY, paint)

    }


    /**
     * 此处代码是为了控制偏移位置超出问题，比如放大有白边，无限移动等
     */
    private fun fixOffset() {
        //宽度最大不超过放大后的最右边(放大后宽度-view宽度后再除以2)
        offsetX = min((bitmap.width * bigScale - width) / 2f, offsetX)
        //宽度最小不小于放大后的最左边，取负值(放大后宽度-view宽度后再除以2)
        offsetX = max(-(bitmap.width * bigScale - width) / 2f, offsetX)
        //高度最大不超过放大后的最下边(放大后高度-view高度后再除以2)
        offsetY = min((bitmap.height * bigScale - height) / 2f, offsetY)
        //高度最小不小于放大后的最上边，取负值(放大后高度-view高度后再除以2)
        offsetY = max(-(bitmap.height * bigScale - height) / 2f, offsetY)

    }

    inner class PhotoScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            //detector.scaleFactor手指放缩系数
            //当return false的时候，说明不消费事件，detector.scaleFactor表示当前状态和初始状态的放缩比
            //当return true的时候，说明消费事件，detector.scaleFactor表示当前状态和上一个状态的放缩比
            Log.e("zjz","detector.scaleFactor="+detector.scaleFactor)
            val tempCurrentScale = currentScale * detector.scaleFactor
            if (tempCurrentScale < smallScale||tempCurrentScale > bigScale) {
                //不消费事件保留之前的位置
                return false
            } else {
                //消费事件，实时更新位置
                currentScale *= detector.scaleFactor
                return true
            }

        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            //跟随手指按下位置进行缩放，进行一个初始偏移
            //获取两指之间的坐标
            offsetX = (detector.focusX - width / 2f) * (1 - bigScale / smallScale)
            offsetY = (detector.focusY - height / 2f) * (1 - bigScale / smallScale)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
        }
    }


    inner class PhotoGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent,
            distanceX: Float, distanceY: Float
        ): Boolean {
            if (big) {
                offsetX -= distanceX
                offsetY -= distanceY
                fixOffset()
                invalidate()
            }
            return false
        }


        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent,
            velocityX: Float, velocityY: Float
        ): Boolean {
            if (big) {
                scroller.fling(
                    offsetX.toInt(),
                    offsetY.toInt(),
                    velocityX.toInt(),
                    velocityY.toInt(),
                    (-(bitmap.width * bigScale - width) / 2).toInt(),
                    ((bitmap.width * bigScale - width) / 2).toInt(),
                    (-(bitmap.height * bigScale - height) / 2).toInt(),
                    ((bitmap.height * bigScale - height) / 2).toInt()
                )
                ViewCompat.postOnAnimation(this@PhotoView, photoFlingRunnable)
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            big = !big
            if (big) {
                //此处是为了让缩放位置按照手指点击处进行放大，进行一个初始偏移
                //手指点击位置-view中间的位置，得到x轴偏移距离，再乘以对应的缩放比例
                offsetX = (e.x - width / 2f) * (1 - bigScale / smallScale)
                //手指点击位置-view中间的位置，得到y轴偏移距离，再乘以对应的缩放比例
                offsetY = (e.y - height / 2f) * (1 - bigScale / smallScale)
                //进行位置修复，避免无限放大缩小或者避免点击图片放大后出现空白
                fixOffset()
                scaleAnimator.start()
            } else {
                scaleAnimator.reverse()
            }
            return true
        }

    }

    inner class PhotoFlingRunnable : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                offsetX = scroller.currX.toFloat()
                offsetY = scroller.currY.toFloat()
                invalidate()
                ViewCompat.postOnAnimation(this@PhotoView, this)
            }
        }

    }

}