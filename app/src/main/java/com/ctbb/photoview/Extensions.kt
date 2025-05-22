package com.ctbb.photoview

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.TypedValue

/**
 * Created by CTBB on 2025-05-21.
 * Describe:
 */

val Float.dps
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )

val Int.dps
    get() = this.toFloat().dps

fun getJayZhou(res: Resources, width: Int): Bitmap {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeResource(res, R.mipmap.img_jayzhou, options)
    options.inJustDecodeBounds = false
    options.inDensity = options.outWidth
    options.inTargetDensity = width
    return BitmapFactory.decodeResource(res, R.mipmap.img_jayzhou, options)
}