package mx.tec.vanttec.dron

import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Size
import org.opencv.features2d.ORB

class DetectorData(image: Mat) {
    val keyPoints = MatOfKeyPoint()
    val descriptors = Mat()
    val size : Size

    init {
        val kp = MatOfKeyPoint()
        val des = Mat()

        orb.detectAndCompute(image, null, kp, des)
        size = image.size()
    }


    // Access to components by decomposition
    // Example: val (kp, ds, sz) = DetectorData(img)
    operator fun component1() = keyPoints
    operator fun component2() = descriptors
    operator fun component3() = size

    companion object {
        private val orb = ORB.create()
    }
}