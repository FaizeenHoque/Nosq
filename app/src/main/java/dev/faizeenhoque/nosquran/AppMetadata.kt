package dev.faizeenhoque.nosquran

/** Public identity; the stable application ID preserves installed app data. */
object AppMetadata {
    const val Name = "nosq"
    const val Developer = "Faizeen Hoque"
    const val SupportEmail = "me@faizeenhoque.dev"
    const val DeveloperWebsite = "https://faizeenhoque.dev"
    const val Website = "https://nosq.faizeenhoque.dev"
    val version: String
        get() = BuildConfig.VERSION_NAME
}
