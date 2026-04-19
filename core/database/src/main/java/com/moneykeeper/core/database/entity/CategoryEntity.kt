package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["parentCategoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("parentCategoryId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val colorHex: String,
    val iconName: String,
    val parentCategoryId: Long? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

fun CategoryEntity.toDomain() = Category(
    id = id, name = name, type = type, colorHex = colorHex, iconName = iconName,
    parentCategoryId = parentCategoryId, isDefault = isDefault, sortOrder = sortOrder,
)

fun Category.toEntity() = CategoryEntity(
    id = id, name = name, type = type, colorHex = colorHex, iconName = iconName,
    parentCategoryId = parentCategoryId, isDefault = isDefault, sortOrder = sortOrder,
)
