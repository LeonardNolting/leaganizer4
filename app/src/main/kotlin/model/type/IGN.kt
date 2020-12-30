package model.type

import model.database.TextWrapperColumnType
import model.database.WrapperColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

// https://youtrack.jetbrains.com/issue/KT-34024
data class IGN(override val value: String) : WrapperColumnType.Wrapper

class IGNColumnType : TextWrapperColumnType<IGN>(IGN::class)

fun Table.ign(name: String): Column<IGN> = registerColumn(name, IGNColumnType())