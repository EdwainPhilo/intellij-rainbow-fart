package com.github.izhangzhihao.rainbow.fart

import com.github.izhangzhihao.rainbow.fart.RainbowFartTypedHandler.FartTypedHandler.releaseFart
import com.github.izhangzhihao.rainbow.fart.settings.RainbowFartSettings
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RainbowFartLookupHandler : LookupListener {
    private var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    override fun itemSelected(event: LookupEvent) {
        try {
            if (!RainbowFartSettings.instance.isRainbowFartEnabled) {
                return
            }
            val currentItem: LookupElement = event.lookup.currentItem ?: return
            val lookupString: String = currentItem.lookupString
            if (BuildInContributes.buildInContributes.containsKey(lookupString)) {
                GlobalScope.launch((defaultDispatcher)) {
                    releaseFart(BuildInContributes.buildInContributes.getOrDefault(lookupString, emptyList()))
                }
            }
        } finally {
            super.itemSelected(event)
        }
    }
}

class RainbowFartLookupComponent(project: Project) {

    init {
        // 使用新的 LookupManagerListener 替代旧的 PropertyChangeListener
        project.messageBus.connect().subscribe(
            LookupManagerListener.TOPIC,
            LookupManagerListener { _, newLookup -> // 这个方法等同于原来的 PropertyChangeListener
                // 当新的 Lookup 创建时会被调用
                newLookup?.addLookupListener(RainbowFartLookupHandler())
            }
        )
    }
}