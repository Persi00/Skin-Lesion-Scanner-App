package com.example.skincancerscannerapp

import android.content.Context
import android.graphics.Bitmap
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream

class SegmentationModel(context: Context) {

    private val model: Module

    val INPUT_WIDTH = 512
    val INPUT_HEIGHT = 512

    val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val STD  = floatArrayOf(0.229f, 0.224f, 0.225f)

    init {
        model = LiteModuleLoader.load(assetFilePath(context, "model.ptl"))
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    fun segment(bitmap: Bitmap): Array<IntArray> {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)

        val inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(resized, MEAN, STD)

        val outputIValue = model.forward(IValue.from(inputTensor))
        val outputMap = outputIValue.toDictStringKey()
        val outputTensor: Tensor = outputMap["out"]!!.toTensor()

        val scores = outputTensor.dataAsFloatArray
        val numClasses = outputTensor.shape()[1].toInt()
        val h = outputTensor.shape()[2].toInt()
        val w = outputTensor.shape()[3].toInt()

        val result = Array(h) { IntArray(w) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var maxClass = 0
                var maxScore = Float.NEGATIVE_INFINITY
                for (c in 0 until numClasses) {
                    val score = scores[c * h * w + y * w + x]
                    if (score > maxScore) {
                        maxScore = score
                        maxClass = c
                    }
                }
                result[y][x] = maxClass
            }
        }

        return result
    }

    fun maskToBitmap(mask: Array<IntArray>, numClasses: Int): Bitmap {
        val h = mask.size
        val w = mask[0].size
        val pixels = IntArray(h * w)

        val colors = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
        )

        for (y in 0 until h) {
            for (x in 0 until w) {
                val classIdx = mask[y][x]
                pixels[y * w + x] = colors[classIdx % colors.size]
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}