package paige.navic.icons.brand

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons

val Icons.Brand.Navidrome: ImageVector
	get() {
		if (_Navidrome != null) return _Navidrome!!

		_Navidrome = ImageVector.Builder(
			name = "Brand.Navidrome",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(
				fill = SolidColor(Color.White),
				pathFillType = PathFillType.EvenOdd
			) {
				moveTo(12f, 2f)
				curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
				reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
				reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
				reflectiveCurveTo(17.52f, 2f, 12f, 2f)
				moveToRelative(0f, 18f)
				curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
				reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
				reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
				reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
			}
			path(
				fill = SolidColor(Color.White),
				pathFillType = PathFillType.EvenOdd
			) {
				moveTo(8.5f, 12f)
				curveToRelative(0f, -0.93f, 0.37f, -1.82f, 1.03f, -2.47f)
				curveToRelative(0.65f, -0.66f, 1.54f, -1.03f, 2.47f, -1.03f)
				reflectiveCurveToRelative(1.82f, 0.37f, 2.47f, 1.03f)
				curveToRelative(0.66f, 0.65f, 1.03f, 1.54f, 1.03f, 2.47f)
				reflectiveCurveToRelative(-0.37f, 1.82f, -1.03f, 2.47f)
				curveToRelative(-0.65f, 0.66f, -1.54f, 1.03f, -2.47f, 1.03f)
				reflectiveCurveToRelative(-1.82f, -0.37f, -2.47f, -1.03f)
				arcTo(3.46f, 3.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.5f, 12f)
				moveToRelative(2f, 0f)
				arcToRelative(1.499f, 1.499f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.56f, -1.06f)
				arcToRelative(1.499f, 1.499f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.12f, 2.12f)
				curveToRelative(-0.28f, -0.28f, -0.44f, -0.66f, -0.44f, -1.06f)
			}
			path(fill = SolidColor(Color.White)) {
				moveTo(7.55f, 10.34f)
				curveToRelative(-0.1f, 0.25f, -0.28f, 0.45f, -0.52f, 0.56f)
				curveToRelative(-0.25f, 0.11f, -0.52f, 0.12f, -0.77f, 0.03f)
				arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.59f, -1.28f)
				arcToRelative(6.8f, 6.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.98f, -3.98f)
				arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.28f, 0.59f)
				curveToRelative(0.09f, 0.25f, 0.08f, 0.52f, -0.03f, 0.77f)
				curveToRelative(-0.11f, 0.24f, -0.31f, 0.42f, -0.56f, 0.52f)
				curveToRelative(-1.29f, 0.48f, -2.31f, 1.5f, -2.79f, 2.79f)
				moveToRelative(8.9f, 3.32f)
				curveToRelative(0.1f, -0.25f, 0.28f, -0.45f, 0.52f, -0.56f)
				curveToRelative(0.25f, -0.11f, 0.52f, -0.12f, 0.77f, -0.03f)
				reflectiveCurveToRelative(0.45f, 0.28f, 0.56f, 0.52f)
				reflectiveCurveToRelative(0.12f, 0.52f, 0.03f, 0.76f)
				arcToRelative(6.8f, 6.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3.98f, 3.98f)
				arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.28f, -0.59f)
				curveToRelative(-0.09f, -0.25f, -0.08f, -0.52f, 0.03f, -0.77f)
				curveToRelative(0.11f, -0.24f, 0.31f, -0.42f, 0.56f, -0.52f)
				curveToRelative(1.29f, -0.48f, 2.31f, -1.5f, 2.79f, -2.79f)
			}
		}.build()

		return _Navidrome!!
	}

@Suppress("ObjectPropertyName")
private var _Navidrome: ImageVector? = null
