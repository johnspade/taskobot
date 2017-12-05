package ru.johnspade.taskobot.service

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import ru.johnspade.taskobot.dao.Language
import java.util.*

@Service
class LocaleService {

	fun setLocale(language: Language) {
		LocaleContextHolder.setLocale(Locale.forLanguageTag(language.languageTag))
	}

}
