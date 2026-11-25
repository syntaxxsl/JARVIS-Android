package com.jarvis.assistant.core.phone

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

/**
 * User-enabled accessibility bridge. It does not act in the background by itself;
 * actions are only invoked from explicit controls in the JARVIS UI.
 */
class JarvisAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    fun readVisibleText(): String {
        val root = rootInActiveWindow ?: return "Ekran içeriğine erişilemiyor."
        val lines = mutableListOf<String>()
        collectText(root, lines)
        return lines.distinct().take(120).joinToString("\n").ifBlank { "Ekranda okunabilir metin bulunamadı." }
    }

    fun clickText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root) { it.text?.toString()?.contains(target, ignoreCase = true) == true ||
            it.contentDescription?.toString()?.contains(target, ignoreCase = true) == true } ?: return false
        return clickableParent(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    fun enterText(targetHint: String, value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root) {
            it.isEditable && (targetHint.isBlank() ||
                it.text?.toString()?.contains(targetHint, true) == true ||
                it.contentDescription?.toString()?.contains(targetHint, true) == true ||
                it.hintText?.toString()?.contains(targetHint, true) == true)
        } ?: findNode(root) { it.isEditable } ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        })
    }

    fun scrollDown(): Boolean = rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    fun scrollUp(): Boolean = rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true

    private fun collectText(node: AccessibilityNodeInfo, output: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
            collectText(child, output)
            child.recycle()
        }
    }

    private fun findNode(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(node)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, match)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun clickableParent(original: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(original)
        repeat(8) {
            val current = node ?: return null
            if (current.isClickable) return current
            val parent = current.parent
            current.recycle()
            node = parent
        }
        return node
    }

    companion object {
        @Volatile private var active: JarvisAccessibilityService? = null
        fun isEnabledInProcess(): Boolean = active != null
        fun readScreen(): String = active?.readVisibleText() ?: "Erişilebilirlik servisi bağlı değil."
        fun click(text: String): Boolean = active?.clickText(text) == true
        fun type(hint: String, value: String): Boolean = active?.enterText(hint, value) == true
        fun scroll(down: Boolean): Boolean = if (down) active?.scrollDown() == true else active?.scrollUp() == true
    }
}
