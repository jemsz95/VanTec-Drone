package mx.tec.vanttec.dron

import com.google.android.gms.maps.model.LatLng
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.core.Size
import org.opencv.core.Mat
import java.util.ArrayList
import org.opencv.core.MatOfPoint
import org.opencv.features2d.FlannBasedMatcher
import org.opencv.imgproc.Moments

typealias VTPoint = Pair<Int, Int>

// colorLow: Scalar(59.0, 69.0, 0.0)
// colorHigh: Scalar(255.0, 255.0, 255.0)

private val vFOV  = Math.toRadians(45.7)
private val hFOV  = Math.toRadians(73.7)
private const val altitude = 20.0
private const val minThresh = 0.0


// Descriptor matching vars
private const val minMatches = 10
private const val flannParamsPath = "/path/to/yaml" // TODO: GET PATH
private val matcher = FlannBasedMatcher.create()

operator fun LatLng.component1() = this.latitude
operator fun LatLng.component2() = this.longitude

fun  obstacles(srcImg: Mat, colorLow: Scalar, colorHigh: Scalar) :
        Triple<List<VTPoint>, VTPoint, VTPoint> {
    val size = srcImg.size()

    //RESIZE
    val imgNoBlur= Mat()
    Imgproc.cvtColor(srcImg ,imgNoBlur ,Imgproc.COLOR_RGB2GRAY);
    val sz = Size(640.0, 480.0)
    Imgproc.resize(srcImg,imgNoBlur, sz)

    //READ RGB
    val imgRGB = Mat()
    Imgproc.resize(srcImg, imgRGB, sz)

    //BLUR GRAYSCALE
    val img = Mat()
    Imgproc.GaussianBlur(imgNoBlur, img, Size(7.0, 7.0), 1.0, 1.0)

    val hsv = Mat()
    Imgproc.cvtColor(imgRGB, hsv, Imgproc.COLOR_RGB2HSV)

    //THRESHOLD HSV
    val imgHsv = Mat()
    Core.inRange(hsv, colorLow, colorHigh, imgHsv); //hsv


    //CANNY AND DILATE
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
    val cannyEdge= Mat()
    val dilated =  Mat()

    Imgproc.Canny(img, cannyEdge, 100.0,80.0);
    Imgproc.dilate(cannyEdge, dilated, kernel)

    //SUM THEM UP
    val final = Mat()
    Core.add(cannyEdge,dilated,final)

    //Get contours
    val contours0 = ArrayList<MatOfPoint>()
    Imgproc.findContours(final, contours0, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)

    //Get moments
    val nu = ArrayList<Moments>(contours0.size)
    for(contour in contours0) {
        nu.add(Imgproc.moments(contour, false))
    }

    val mc = ArrayList<Point>(contours0.size)
    for ((i,n) in nu.withIndex()) {
        if (Imgproc.contourArea(contours0[i]) > minThresh) {
            mc.add(Point(n.m10 / n.m00, n.m01 / n.m00))
        }
    }

    val (totalMetersX, totalMetersY) = getDistanceFieldOfView(altitude, hFOV, vFOV)

    //Draw contours
    val output = ArrayList<VTPoint>();
    for (c in mc) {
        //I draw a black little empty circle in the centroid position
        Imgproc.circle(imgRGB, c, 5, Scalar(255.0, 0.0, 0.0), -1)
        val yModified = 480 - c.y
        val x = Math.round((c.x * totalMetersX * 8.55) / size.width).toInt()
        val y = Math.round((yModified * totalMetersY * 7.6) / size.height).toInt()
        Imgproc.putText(imgRGB, "($x,$y)", c, Imgproc.FONT_HERSHEY_COMPLEX, 0.3, Scalar(0.0, 0.0, 0.0))

        output.add(Pair(x, y))
    }

    return Triple(output, VTPoint(0,0), VTPoint(0,0))
}

private fun getDistanceFieldOfView(h: Double, hFOV: Double, vFOV: Double) : Pair<Double,Double> {
    val xDist = Math.tan(hFOV / 2) * h * 2
    val yDist = Math.tan(vFOV / 2) * h * 2

    return Pair(xDist, yDist)
}

fun findInImage(image: Mat, template: DetectorData) : Mat? {
    val (tkp, tdes, tsize) = template
    val (qkp, qdes) = DetectorData(image)

    val matches = ArrayList<MatOfDMatch>()
    val goodMatches = ArrayList<DMatch>()

    matcher.read(flannParamsPath)
    matcher.knnMatch(qdes, tdes, matches, 2)

    for(match in matches) {
        val match = match.toList()

        if(match.size == 2) {
            val (m,n) = match

            if(m.distance < 0.7 * n.distance)
                goodMatches.add(n)
        }
    }

    if(goodMatches.size > minMatches) {
        val srcPts = ArrayList<Point>()
        val dstPts = ArrayList<Point>()

        val qkp = qkp.toList()
        val tkp = tkp.toList()

        for(m in goodMatches) {
            srcPts.add(qkp[m.queryIdx].pt)
            dstPts.add(tkp[m.trainIdx].pt)
        }

        val srcPtsMat = MatOfPoint2f(*srcPts.toTypedArray())
        val dstPtsMat = MatOfPoint2f(*dstPts.toTypedArray())

        val mat = Calib3d.findHomography(srcPtsMat, dstPtsMat, Calib3d.RANSAC, 5.0)

        val foundImg = Mat()

        Imgproc.warpPerspective(image, foundImg, mat, tsize)

        return foundImg
    }

    return null
}