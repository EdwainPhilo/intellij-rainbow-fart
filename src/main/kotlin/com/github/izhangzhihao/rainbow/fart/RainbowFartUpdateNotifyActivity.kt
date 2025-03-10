package com.github.izhangzhihao.rainbow.fart

import com.github.izhangzhihao.rainbow.fart.settings.RainbowFartSettings
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.ide.startup.StartupActionScriptManager.DeleteCommand
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class RainbowFartUpdateNotifyActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        removeIfInstalled()
        val settings = RainbowFartSettings.instance
        if (getPlugin()?.version != settings.version) {
            settings.version = getPlugin()!!.version
            showUpdate(project)
        }
    }

    private fun removeIfInstalled() {
        val pluginId = PluginId.getId("com.github.jadepeng.rainbowfart")
        val isInstalled = PluginManagerCore.isPluginInstalled(pluginId)
        if (isInstalled) {
            val pluginDescriptor = PluginManagerCore.getPlugin(pluginId)
            if (pluginDescriptor != null) {
                StartupActionScriptManager.addActionCommand(DeleteCommand(pluginDescriptor.pluginPath))
            }
        }
    }

    companion object {
        private const val PLUGIN_ID = "izhangzhihao.rainbow.fart"
        private var isNotified = false
        private val updateContent = """
            <br/>欢迎使用 Rainbow Fart！
            <br/>如果您觉得这个插件很有趣，请给我们<a href="https://github.com/izhangzhihao/intellij-rainbow-fart">一个Star</a>，您的支持是我们最大的动力！
            <br/>
            <br/>Welcome to Rainbow Fart!
            <br/>If you find this plugin interesting, please <a href="https://github.com/izhangzhihao/intellij-rainbow-fart">give us a star</a>. Your support is our biggest motivation!
        """.trimIndent()

        fun getPlugin(): IdeaPluginDescriptor? = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))

        private fun updateMsg(): String {
            val plugin = getPlugin()
            return if (plugin != null) {
                "Rainbow Fart ${plugin.version}"
            } else {
                "Rainbow Fart"
            }
        }

        private fun showUpdate(project: Project) {
            val notification = createNotification(
                updateMsg(),
                updateContent,
                NotificationType.INFORMATION,
                NotificationListener.UrlOpeningListener(false)
            )
            showFullNotification(project, notification)
        }
    }
}
