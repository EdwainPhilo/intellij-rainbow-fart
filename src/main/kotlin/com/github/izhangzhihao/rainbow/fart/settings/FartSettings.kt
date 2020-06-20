package com.github.izhangzhihao.rainbow.fart.settings

import com.intellij.openapi.components.ServiceManager.getService
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil.copyBean
import org.jetbrains.annotations.Nullable

@State(name = "FartSettings", storages = [(Storage("rainbow_fart.xml"))])
class FartSettings : PersistentStateComponent<FartSettings> {

    var isRainbowFartEnabled = true
    var version = "Unknown"

    @Nullable
    override fun getState() = this

    override fun loadState(state: FartSettings) {
        copyBean(state, this)
    }

    companion object {
        val instance: FartSettings
            get() = getService(FartSettings::class.java)
    }
}