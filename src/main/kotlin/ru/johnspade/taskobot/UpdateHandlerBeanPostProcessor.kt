package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Order(100)
@Component
class UpdateHandlerBeanPostProcessor @Autowired constructor(private val botControllerContainer: BotControllerContainer):
		BeanPostProcessor {

	private val controllers = mutableMapOf<String, Class<Any>>()

	override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
		val beanClass = bean.javaClass
		if (beanClass.isAnnotationPresent(BotController::class.java))
			controllers[beanName] = beanClass
		return bean
	}

	override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
		if (!controllers.containsKey(beanName))
			return bean
		val original = controllers.getValue(beanName)
		original.methods.filter { it.isAnnotationPresent(CallbackQueryMapping::class.java) }.forEach {
			val callbackQueryMapping = it.getAnnotation(CallbackQueryMapping::class.java)
			botControllerContainer.addCallbackQueryController(callbackQueryMapping.callbackDataType,
					BotApiMethodController(bean, it))
		}
		original.methods.filter { it.isAnnotationPresent(MessageMapping::class.java) }.forEach {
			val messageMapping = it.getAnnotation(MessageMapping::class.java)
			botControllerContainer.addMessageController(messageMapping.message, BotApiMethodController(bean, it))
		}
		return bean
	}

}
