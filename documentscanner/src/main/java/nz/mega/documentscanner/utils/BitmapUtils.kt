package nz.mega.documentscanner.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.net.Uri
import androidx.annotation.IntRange
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSources
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.common.ImageDecodeOptions
import com.facebook.imagepipeline.common.RotationOptions
import com.facebook.imagepipeline.image.CloseableBitmap
import com.facebook.imagepipeline.request.ImageRequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object BitmapUtils {

    @Suppress("UNCHECKED_CAST")
    suspend fun getBitmapFromUri(
        imageUri: Uri,
        degreesToRotate: Int = 0,
        @IntRange(from = 0, to = 100) quality: Int = 100
    ): Bitmap = withContext(Dispatchers.Default) {
        val imageRequest = ImageRequestBuilder.newBuilderWithSource(imageUri)
            .disableDiskCache()
            .disableMemoryCache()
            .setImageDecodeOptions(ImageDecodeOptions.defaults())

        if (degreesToRotate != 0) {
            imageRequest.rotationOptions = RotationOptions.forceRotation(degreesToRotate)
        }

        val dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest.build(), this)
        val result = DataSources.waitForFinalResult(dataSource) as CloseableReference<CloseableBitmap>
        val resultBitmap = result.get().underlyingBitmap
        dataSource.close()

        resultBitmap.compress(quality)
    }

    suspend fun Bitmap.compress(quality: Int) =
        withContext(Dispatchers.Default) {
            val outputStream = ByteArrayOutputStream()
            compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            BitmapFactory.decodeStream(ByteArrayInputStream(outputStream.toByteArray()))
        }

    suspend fun Bitmap.rotate(degrees: Int): Bitmap =
        withContext(Dispatchers.Default) {
            if (degrees == 0) return@withContext this@rotate

            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(this@rotate, 0, 0, width, height, matrix, true).also {
                this@rotate.recycle()
            }
        }

    suspend fun Image.toBitmap(): Bitmap =
        withContext(Dispatchers.Default) {
            require(format == ImageFormat.JPEG)

            val buffer = planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

    fun Bitmap.toMat(): Mat {
        val mat = Mat(height, width, CvType.CV_8U, Scalar(4.0))
        val bitmap32 = copy(Bitmap.Config.ARGB_8888, true)
        Utils.bitmapToMat(bitmap32, mat)
        return mat
    }

    fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }
}
