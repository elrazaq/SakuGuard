package com.sakuguard.app.feature.transactions

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.Category

object CategoryVisuals {

    @StringRes
    fun localizedNameRes(categoryId: String): Int? = when (categoryId) {
        "cat-inc-salary" -> R.string.cat_salary
        "cat-inc-freelance" -> R.string.cat_freelance
        "cat-inc-bonus" -> R.string.cat_bonus
        "cat-inc-business" -> R.string.cat_business
        "cat-inc-investment" -> R.string.cat_investment
        "cat-inc-other" -> R.string.cat_other_income
        "cat-exp-food" -> R.string.cat_food
        "cat-exp-transport" -> R.string.cat_transportation
        "cat-exp-shopping" -> R.string.cat_shopping
        "cat-exp-household" -> R.string.cat_household
        "cat-exp-bills" -> R.string.cat_bills
        "cat-exp-health" -> R.string.cat_health
        "cat-exp-education" -> R.string.cat_education
        "cat-exp-entertainment" -> R.string.cat_entertainment
        "cat-exp-travel" -> R.string.cat_travel
        "cat-exp-other" -> R.string.cat_other_expenses
        else -> null
    }

    @Composable
    fun localizedName(category: Category): String {
        val res = localizedNameRes(category.id)
        return if (res != null) androidx.compose.ui.res.stringResource(res) else category.name
    }

    fun resolveIcon(iconKey: String): ImageVector = when (iconKey) {
        "payments" -> Icons.Outlined.Payments
        "work" -> Icons.Outlined.WorkOutline
        "redeem" -> Icons.Outlined.Redeem
        "storefront" -> Icons.Outlined.Storefront
        "trending_up" -> Icons.Outlined.TrendingUp
        "add_circle" -> Icons.Outlined.AddCircleOutline
        "restaurant" -> Icons.Outlined.Restaurant
        "directions_car" -> Icons.Outlined.DirectionsCar
        "shopping_bag" -> Icons.Outlined.ShoppingBag
        "home" -> Icons.Outlined.Home
        "receipt_long" -> Icons.Outlined.ReceiptLong
        "medical_services" -> Icons.Outlined.MedicalServices
        "school" -> Icons.Outlined.School
        "movie" -> Icons.Outlined.Movie
        "flight" -> Icons.Outlined.Flight
        else -> Icons.Outlined.MoreHoriz
    }

    fun resolveColor(colorHex: Long): Color = Color(colorHex)
}
