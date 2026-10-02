package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class AppReleaseVersionTest {
    @Test fun versionsUseNumericComparisonAndProjectTags() {
        assertTrue(AppReleaseVersion.parse("AndroidPlay-1.10.0")!! > AppReleaseVersion.parse("v1.9.9")!!)
        assertEquals(AppReleaseVersion(1, 2, 0), AppReleaseVersion.parse("AndroidPlay-1.2.0-arm64"))
        assertEquals(AppReleaseVersion(1, 2, 0), AppReleaseVersion.parse("1.2.0"))
        assertTrue(AppReleaseVersion.parse("1.2.1")!! > AppReleaseVersion.parse("1.2.0")!!)
        assertNull(AppReleaseVersion.parse("nightly"))
        assertNull(AppReleaseVersion.parse("9999999999999999.1.1"))
    }
}
