package com.github.izhangzhihao.rainbow.fart

import com.github.izhangzhihao.rainbow.fart.RainbowFartTypedHandler.FartTypedHandler.isTypingInsideComment
import com.github.izhangzhihao.rainbow.fart.RainbowFartTypedHandler.FartTypedHandler.releaseFart
import com.github.izhangzhihao.rainbow.fart.settings.RainbowFartSettings
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import javazoom.jl.player.Player
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine

class RainbowFartTypedHandler(originalHandler: TypedActionHandler) : TypedActionHandlerBase(originalHandler) {

    private var candidates: MutableList<Char> = mutableListOf()
    private var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        try {
            if (!RainbowFartSettings.instance.isRainbowFartEnabled) {
                return
            }
            val project = editor.project

            if (project != null) {
                //val language = PsiUtilBase.getLanguageInEditor(editor, project)
                val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
                if (virtualFile != null) {
                    val file = PsiManager.getInstance(project).findFile(virtualFile)
                    if (file != null && isTypingInsideComment(editor, file)) {
                        return
                    }
                }
            }

            candidates.add(charTyped)
            val str = candidates.joinToString("")
            BuildInContributes.buildInContributesSeq
                .firstOrNull { (keyword, _) ->
                    str.contains(keyword, true)
                }?.let { (_, voices) ->
                    GlobalScope.launch(defaultDispatcher) {
                        releaseFart(voices)
                    }
                    candidates.clear()
                }
            if (candidates.size > 20) {
                candidates = candidates.subList(10, candidates.size - 1)
            }
        } finally {
            // Ensure original handler is called no matter what errors are thrown, to prevent typing from being lost.
            try {
                this.myOriginalHandler?.execute(editor, charTyped, dataContext)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    object FartTypedHandler {

        private var playing = false

        fun releaseFart(voices: List<String>) {
            if (RainbowFartSettings.instance.isRainbowFartEnabled && !playing) {
                playing = true
                playVoice(voices)
                playing = false
            }
        }

        private fun playVoice(voices: List<String>) {
            try {
                val voiceFile = voices.random()
                // 使用try-catch处理可能的API不兼容问题
                val fileExtension = voiceFile.substringAfterLast('.', "").lowercase()


                // 添加wav和其他音频格式支持，如果Java Sound API不支持，则回退到MP3播放尝试
                when (fileExtension) {
                    "mp3" -> playMP3(voiceFile)
                    "wav" -> playWAV(voiceFile)
                    else -> {
                        // 尝试使用Java Sound API播放其他格式
                        playWithJavaSound(voiceFile)
                    }
                }
            } catch (e: Throwable) {
                // 记录错误但不中断用户体验
                e.printStackTrace()
            }
        }

        private fun playMP3(voiceFile: String) {
            val mp3Stream =
                if (RainbowFartSettings.instance.customVoicePackage != "") {
                    resolvePath(RainbowFartSettings.instance.customVoicePackage + File.separator + voiceFile).inputStream()
                } else {
                    FartTypedHandler::class.java.getResourceAsStream("/build-in-voice-chinese/$voiceFile")
                }
            val player = Player(mp3Stream)
            player.play()
            player.close()
        }

        private fun playWAV(voiceFile: String) {
            val audioInputStream = if (RainbowFartSettings.instance.customVoicePackage != "") {
                AudioSystem.getAudioInputStream(
                    resolvePath(RainbowFartSettings.instance.customVoicePackage + File.separator + voiceFile)
                )
            } else {
                AudioSystem.getAudioInputStream(
                    FartTypedHandler::class.java.getResourceAsStream("/build-in-voice-chinese/$voiceFile")
                )
            }

            val clip = AudioSystem.getClip()
            clip.open(audioInputStream)
            clip.start()

            // 等待播放完成
            while (clip.isRunning) {
                Thread.sleep(10)
            }
            clip.close()
            audioInputStream.close()
        }

        private fun playWithJavaSound(voiceFile: String) {
            try {
                val audioInputStream = if (RainbowFartSettings.instance.customVoicePackage != "") {
                    AudioSystem.getAudioInputStream(
                        resolvePath(RainbowFartSettings.instance.customVoicePackage + File.separator + voiceFile)
                    )
                } else {
                    AudioSystem.getAudioInputStream(
                        FartTypedHandler::class.java.getResourceAsStream("/build-in-voice-chinese/$voiceFile")
                    )
                }

                val format = audioInputStream.format
                val info = DataLine.Info(Clip::class.java, format)

                if (AudioSystem.isLineSupported(info)) {
                    val clip = AudioSystem.getLine(info) as Clip
                    clip.open(audioInputStream)
                    clip.start()

                    // 等待播放完成
                    while (clip.isRunning) {
                        Thread.sleep(10)
                    }
                    clip.close()
                }
                audioInputStream.close()
            } catch (e: Exception) {
                // 如果Java Sound API不支持，回退到MP3播放尝试
                playMP3(voiceFile)
            }
        }

        fun isTypingInsideComment(editor: Editor, file: PsiFile): Boolean {
            val provider = file.viewProvider
            val offset = editor.caretModel.offset
            val elementAtCaret: PsiElement? = if (offset < editor.document.textLength) {
                provider.findElementAt(offset)
            } else {
                provider.findElementAt(editor.document.textLength - 1)
            }
            var element = elementAtCaret
            while (element is PsiWhiteSpace) {
                element = element.getPrevSibling()
            }

            return element.elementType.toString().contains("comment", true)
//            if (element == null) {
//                return false
//            }
//            val node: ASTNode? = element.node
//            return node != null //&& JavaDocTokenType.ALL_JAVADOC_TOKENS.contains(node.getElementType())
        }
    }
}