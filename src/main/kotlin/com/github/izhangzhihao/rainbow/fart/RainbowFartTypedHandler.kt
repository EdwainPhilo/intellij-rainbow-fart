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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

class RainbowFartTypedHandler(originalHandler: TypedActionHandler) : TypedActionHandlerBase(originalHandler) {

    private var candidates: MutableList<Char> = mutableListOf()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        try {
            if (!RainbowFartSettings.instance.isRainbowFartEnabled) {
                return
            }
            val project = editor.project

            if (project != null) {
                val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
                if (virtualFile != null) {
                    val file = PsiManager.getInstance(project).findFile(virtualFile)
                    if (file != null && isTypingInsideComment(editor, file)) {
                        return
                    }
                }
            }

            // 获取当前光标位置的前一个字符
            val offset = editor.caretModel.offset
            val document = editor.document
            val text = document.text

            candidates.add(charTyped)
            val str = candidates.joinToString("")


            // 如果当前输入的内容不在实际文本中，说明是删除操作
            if (!text.contains(str)) {
                candidates.clear()

                // 获取当前行的开始位置
                val lineNumber = document.getLineNumber(offset)
                val lineStartOffset = document.getLineStartOffset(lineNumber)

                // 从当前位置向前最多读取20个字符，但不超过行首
                val start = maxOf(lineStartOffset, offset - 20)
                val currentText = text.substring(start, offset)
                    .replace("\n", "") // 移除可能的换行符
                    .replace("\r", "") // 移除可能的回车符

                candidates.addAll(currentText.toList())
            }

            BuildInContributes.buildInContributesSeq
                .firstOrNull { (keyword, _) ->
                    str.contains(keyword, true)
                }?.let { (_, voices) ->
                    scope.launch {
                        releaseFart(voices)
                    }
                    candidates.clear()
                }
            if (candidates.size > 20) {
                candidates = candidates.subList(10, candidates.size - 1)
            }
        } finally {
            try {
                this.myOriginalHandler?.execute(editor, charTyped, dataContext)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    object FartTypedHandler {
        @Volatile
        private var playing = false
        private val playLock = Object()
        private var currentClip: javax.sound.sampled.Clip? = null
        private var currentPlayer: Player? = null

        fun releaseFart(voices: List<String>) {
            if (!RainbowFartSettings.instance.isRainbowFartEnabled || playing) {
                return
            }

            synchronized(playLock) {
                if (playing) return
                playing = true
                try {
                    // 确保之前的音频停止
                    stopCurrentAudio()
                    playVoice(voices)
                } finally {
                    playing = false
                }
            }
        }

        private fun stopCurrentAudio() {
            currentClip?.apply {
                stop()
                close()
            }
            currentClip = null

            currentPlayer?.close()
            currentPlayer = null
        }

        private fun playVoice(voices: List<String>) {
            try {
                val voiceFile = voices.random()
                val fileExtension = voiceFile.substringAfterLast('.', "").lowercase()

                when (fileExtension) {
                    "mp3" -> playMP3(voiceFile)
                    "wav" -> playWAV(voiceFile)
                    else -> playMP3(voiceFile)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                playing = false
            }
        }

        private fun playMP3(voiceFile: String) {
            try {
                val mp3Stream = if (RainbowFartSettings.instance.customVoicePackage != "") {
                    resolvePath(RainbowFartSettings.instance.customVoicePackage + File.separator + voiceFile)
                        .inputStream()
                } else {
                    FartTypedHandler::class.java.getResourceAsStream("/build-in-voice-chinese/$voiceFile")
                } ?: return

                currentPlayer = Player(mp3Stream)
                currentPlayer?.play()
                currentPlayer?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun playWAV(voiceFile: String) {
            try {
                val audioFile = if (RainbowFartSettings.instance.customVoicePackage != "") {
                    resolvePath(RainbowFartSettings.instance.customVoicePackage + File.separator + voiceFile)
                } else {
                    val tempFile = File.createTempFile("temp", ".wav")
                    tempFile.deleteOnExit()
                    FartTypedHandler::class.java.getResourceAsStream("/build-in-voice-chinese/$voiceFile")
                        ?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    tempFile
                }

                val clip = AudioSystem.getClip()
                currentClip = clip
                val audioInputStream = AudioSystem.getAudioInputStream(audioFile)
                clip.open(audioInputStream)

                clip.addLineListener { event ->
                    if (event.type == LineEvent.Type.STOP) {
                        playing = false
                        clip.close()
                        audioInputStream.close()
                    }
                }

                clip.start()
                // 等待播放完成
                while (clip.isRunning) {
                    Thread.sleep(10)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        }
    }
}