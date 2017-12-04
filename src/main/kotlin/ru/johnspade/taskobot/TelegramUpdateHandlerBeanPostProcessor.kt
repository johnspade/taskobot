package ru.johnspade.taskobot

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Order(100)
@Component
class TelegramUpdateHandlerBeanPostProcessor: BeanPostProcessor {

	private val container = BotControllerContainer
	private val controllers = mutableMapOf<String, Class<Any>>()

	override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
		val beanClass = bean.javaClass
		if (beanClass.isAnnotationPresent(BotController::class.java))
			controllers.put(beanName, beanClass)
		return bean
	}

	override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
		if (!controllers.containsKey(beanName))
			return bean
		val original = controllers.getValue(beanName)
		original.methods.filter { it.isAnnotationPresent(CallbackQueryMapping::class.java) }.forEach {
			val callbackQueryMapping = it.getAnnotation(CallbackQueryMapping::class.java)
			container.addCallbackQueryController(callbackQueryMapping.callbackDataType, BotApiMethodController(bean, it))
		}
		original.methods.filter { it.isAnnotationPresent(MessageMapping::class.java) }.forEach {
			val messageMapping = it.getAnnotation(MessageMapping::class.java)
			container.addMessageController(messageMapping.message, BotApiMethodController(bean, it))
		}
		return bean
	}

}