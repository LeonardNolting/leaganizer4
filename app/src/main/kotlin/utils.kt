import com.jessecorbett.diskord.api.model.User

val User.userNameDiscriminator get() = "$username#$discriminator"

val Throwable.displayMessage
	get(): String {
		var e = this
		while (e.message.orEmpty().isEmpty() && e.cause != null) e = e.cause!!
		return e.message.orEmpty().ifEmpty { Config.Commands.defaultErrorMessage + " Type: ${e::class.qualifiedName}" }
	}

fun String.withMultiLineCode(language: String = "") = if (isBlank()) "" else "```$language\n$this\n```"

fun String.replaceAll(vararg pairs: Pair<Char, Char>) =
	pairs.fold(this) { acc, (old, new) ->
		acc.replace(old, new)
	}

fun Int.toStringAlternative() = toString()
	.replaceAll(
		'0' to '\uFF10',
		'1' to '\uFF11',
		'2' to '\uFF12',
		'3' to '\uFF13',
		'4' to '\uFF14',
		'5' to '\uFF15',
		'6' to '\uFF16',
		'7' to '\uFF17',
		'8' to '\uFF18',
		'9' to '\uFF19',
	)

fun String.cutOverflow(maxLength: Int) =
	if (length < maxLength) this
	else substring(0..maxLength - 3) + Typography.ellipsis