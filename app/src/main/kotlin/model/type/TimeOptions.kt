package model.type

import model.database.IntegerWrapperColumnType
import model.database.WrapperColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

// https://youtrack.jetbrains.com/issue/KT-34024
data class TimeOptions(override val value: Int) : WrapperColumnType.Wrapper

class TimeOptionsColumnType : IntegerWrapperColumnType<TimeOptions>(TimeOptions::class)

fun Table.timeOptions(name: String): Column<TimeOptions> = registerColumn(name, TimeOptionsColumnType())