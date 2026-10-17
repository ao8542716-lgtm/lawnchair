package app.lawnchair.glass

/** Flags read from Launcher3 Java code that has to stop drawing its own background. */
object GlassHooks {
    /** When true, closed folder icons skip their flat background; a glass tile sits behind them. */
    @JvmField
    var folderIconGlass: Boolean = false
}
