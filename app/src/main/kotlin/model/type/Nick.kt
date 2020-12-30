package model.type

import model.database.TextWrapperColumnType
import model.database.WrapperColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

// https://youtrack.jetbrains.com/issue/KT-34024
data class Nick(override val value: String) : WrapperColumnType.Wrapper

class NickColumnType : TextWrapperColumnType<Nick>(Nick::class)

fun Table.nick(name: String): Column<Nick> = registerColumn(name, NickColumnType())