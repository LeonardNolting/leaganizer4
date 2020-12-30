package command

import Config.Help

@Command(
	"help",
	description = "Get help for this bot."
)
suspend fun help() = Help.help()

@Command(
	"help", "firstSteps",
	description = "Suggestions for what to do with the bot."
)
suspend fun firstSteps() = Help.firstSteps()

/*@Command(
	"help", "linking",
	description = "Instructions on how to link your discord account on hypixel."
)
suspend fun linking() = Help.linking()*/

@Command(
	"help", "formatting",
	description = "Formatting rules for this bot."
)
suspend fun formatting() = Help.formatting()

@Command(
	"help", "formatting", "extras",
	description = "Formatting rules for this bot."
)
suspend fun formattingExtras() = Help.formattingExtras()

@Command(
	"help", "examples",
	description = "Some examples of using commands."
)
suspend fun examples() = Help.examples()

@Command(
	"help", "tournaments",
	description = "Basic overview of the tournament system."
)
suspend fun tournaments() = Help.tournaments()

@Command(
	"help", "definitions",
	description = "A list of definitions of commonly used words."
)
suspend fun definitions() = Help.definitions()

@Command(
	"help", "faq",
	description = "Answers on frequently asked questions."
)
suspend fun faq() = Help.faq()

@Command(
	"help", "roles",
	description = "A list of all roles and some extra information."
)
suspend fun roles() = Help.roles()

@Command(
	"help", "timezones",
	description = "Get help with timezones."
)
suspend fun timezones() = Help.timezones()

@Command(
	"credits",
	description = "Some sources for helpful information!"
)
suspend fun credits() = Help.credits()