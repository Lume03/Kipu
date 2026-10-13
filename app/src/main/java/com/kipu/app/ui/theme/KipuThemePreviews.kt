package com.kipu.app.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Previews must wrap the real component in KipuTheme to inherit this configuration. */
@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 390, heightDp = 844)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class KipuThemePreviews
