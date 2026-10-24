package tech.stpnk.freedomcat.bg

import java.io.Closeable

interface AbstractInstance : Closeable {

    fun launch()

}