package ru.johnspade.taskobot.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class Messages @Autowired constructor(private val messageSource: MessageSource) {

	fun get(id: String, params: Array<Any>? = null, locale: Locale = LocaleContextHolder.getLocale()): String =
			messageSource.getMessage(id, params, locale)

}
