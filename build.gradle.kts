// Root build script — plugin versions are pinned (no dynamic "+" versions).
// Version choices are justified in docs/build-setup.md.
plugins {
    id("com.android.application") version "8.12.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
