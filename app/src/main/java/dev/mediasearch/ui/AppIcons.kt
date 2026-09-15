package dev.mediasearch.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small filled Material-style vectors kept local so the app does not need extended icons. */
object AppIcons {
    val Explore: ImageVector by lazy {
        ImageVector.Builder(
            name = "Explore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(14.19f, 14.19f)
                lineTo(6f, 18f)
                lineTo(9.81f, 9.81f)
                lineTo(18f, 6f)
                close()
                moveTo(12f, 10.9f)
                curveTo(11.39f, 10.9f, 10.9f, 11.39f, 10.9f, 12f)
                curveTo(10.9f, 12.61f, 11.39f, 13.1f, 12f, 13.1f)
                curveTo(12.61f, 13.1f, 13.1f, 12.61f, 13.1f, 12f)
                curveTo(13.1f, 11.39f, 12.61f, 10.9f, 12f, 10.9f)
                close()
            }
        }.build()
    }

    val Person: ImageVector by lazy {
        ImageVector.Builder(
            name = "Person",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
                curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
                curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
                curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
                close()
                moveTo(12f, 14f)
                curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
                lineTo(4f, 20f)
                lineTo(20f, 20f)
                lineTo(20f, 18f)
                curveTo(20f, 15.34f, 14.67f, 14f, 12f, 14f)
                close()
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(19.43f, 12.98f)
                curveTo(19.47f, 12.66f, 19.5f, 12.34f, 19.5f, 12f)
                curveTo(19.5f, 11.66f, 19.47f, 11.34f, 19.42f, 11.02f)
                lineTo(21.54f, 9.37f)
                curveTo(21.73f, 9.22f, 21.78f, 8.95f, 21.66f, 8.73f)
                lineTo(19.66f, 5.27f)
                curveTo(19.54f, 5.05f, 19.27f, 4.96f, 19.05f, 5.05f)
                lineTo(16.56f, 6.05f)
                curveTo(16.04f, 5.66f, 15.48f, 5.32f, 14.87f, 5.07f)
                lineTo(14.5f, 2.42f)
                curveTo(14.46f, 2.18f, 14.25f, 2f, 14f, 2f)
                lineTo(10f, 2f)
                curveTo(9.75f, 2f, 9.54f, 2.18f, 9.5f, 2.42f)
                lineTo(9.13f, 5.08f)
                curveTo(8.52f, 5.33f, 7.95f, 5.67f, 7.44f, 6.06f)
                lineTo(4.95f, 5.05f)
                curveTo(4.73f, 4.97f, 4.46f, 5.05f, 4.34f, 5.27f)
                lineTo(2.34f, 8.73f)
                curveTo(2.21f, 8.95f, 2.27f, 9.22f, 2.46f, 9.37f)
                lineTo(4.58f, 11.02f)
                curveTo(4.53f, 11.34f, 4.5f, 11.67f, 4.5f, 12f)
                curveTo(4.5f, 12.33f, 4.53f, 12.66f, 4.58f, 12.98f)
                lineTo(2.46f, 14.63f)
                curveTo(2.27f, 14.78f, 2.22f, 15.05f, 2.34f, 15.27f)
                lineTo(4.34f, 18.73f)
                curveTo(4.46f, 18.95f, 4.73f, 19.04f, 4.95f, 18.95f)
                lineTo(7.44f, 17.94f)
                curveTo(7.96f, 18.33f, 8.52f, 18.67f, 9.13f, 18.92f)
                lineTo(9.5f, 21.58f)
                curveTo(9.54f, 21.82f, 9.75f, 22f, 10f, 22f)
                lineTo(14f, 22f)
                curveTo(14.25f, 22f, 14.46f, 21.82f, 14.5f, 21.58f)
                lineTo(14.87f, 18.92f)
                curveTo(15.48f, 18.67f, 16.04f, 18.33f, 16.56f, 17.94f)
                lineTo(19.05f, 18.95f)
                curveTo(19.27f, 19.04f, 19.54f, 18.95f, 19.66f, 18.73f)
                lineTo(21.66f, 15.27f)
                curveTo(21.78f, 15.05f, 21.73f, 14.78f, 21.54f, 14.63f)
                lineTo(19.43f, 12.98f)
                close()
                moveTo(12f, 15.5f)
                curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
                curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
                curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
                curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
                close()
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.5f, 3f)
                curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
                curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
                curveTo(11.09f, 16f, 12.55f, 15.43f, 13.68f, 14.48f)
                lineTo(19.2f, 20f)
                lineTo(20.5f, 18.7f)
                lineTo(14.98f, 13.18f)
                curveTo(15.93f, 12.05f, 16.5f, 10.59f, 16.5f, 9.5f)
                curveTo(16.5f, 5.91f, 13.59f, 3f, 9.5f, 3f)
                close()
                moveTo(9.5f, 5f)
                curveTo(12.0f, 5f, 14f, 7f, 14f, 9.5f)
                curveTo(14f, 12f, 12f, 14f, 9.5f, 14f)
                curveTo(7f, 14f, 5f, 12f, 5f, 9.5f)
                curveTo(5f, 7f, 7f, 5f, 9.5f, 5f)
                close()
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 11f)
                lineTo(7.83f, 11f)
                lineTo(13.42f, 5.41f)
                lineTo(12f, 4f)
                lineTo(4f, 12f)
                lineTo(12f, 20f)
                lineTo(13.41f, 18.59f)
                lineTo(7.83f, 13f)
                lineTo(20f, 13f)
                close()
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.29f, 6.71f)
                lineTo(14.58f, 12f)
                lineTo(9.29f, 17.29f)
                lineTo(10.71f, 18.71f)
                lineTo(17.41f, 12f)
                lineTo(10.71f, 5.29f)
                close()
            }
        }.build()
    }
}
