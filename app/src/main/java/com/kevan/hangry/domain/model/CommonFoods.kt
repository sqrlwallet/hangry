package com.kevan.hangry.domain.model

import com.kevan.hangry.data.local.entity.savedMealKey

/**
 * A built-in everyday food with typical values for one standard serving (USDA FoodData Central
 * reference amounts, rounded). Lets someone log a banana or an egg in one tap on day one, before
 * they have any saved meals of their own.
 */
data class CommonFood(
    val name: String,
    val serving: String,
    val category: CommonFoodCategory,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0
) {
    val key: String get() = savedMealKey(name)
}

enum class CommonFoodCategory(val title: String) {
    FRUIT("Fruit"),
    PROTEIN("Protein"),
    GRAINS("Grains & bread"),
    DAIRY("Dairy"),
    VEGETABLES("Vegetables"),
    SNACKS("Nuts & snacks"),
    DRINKS("Drinks")
}

object CommonFoods {
    private val F = CommonFoodCategory.FRUIT
    private val P = CommonFoodCategory.PROTEIN
    private val G = CommonFoodCategory.GRAINS
    private val D = CommonFoodCategory.DAIRY
    private val V = CommonFoodCategory.VEGETABLES
    private val S = CommonFoodCategory.SNACKS
    private val B = CommonFoodCategory.DRINKS

    val all: List<CommonFood> = listOf(
        CommonFood("Banana", "1 medium (118 g)", F, 105, 1.3, 27.0, 0.4, fiberG = 3.1, sugarG = 14.4, sodiumMg = 1.0),
        CommonFood("Apple", "1 medium (182 g)", F, 95, 0.5, 25.0, 0.3, fiberG = 4.4, sugarG = 18.9, sodiumMg = 2.0),
        CommonFood("Orange", "1 medium (131 g)", F, 62, 1.2, 15.4, 0.2, fiberG = 3.1, sugarG = 12.2),
        CommonFood("Pear", "1 medium (178 g)", F, 101, 0.6, 27.0, 0.2, fiberG = 5.5, sugarG = 17.0, sodiumMg = 2.0),
        CommonFood("Grapes", "1 cup (151 g)", F, 104, 1.1, 27.3, 0.2, fiberG = 1.4, sugarG = 23.4, sodiumMg = 3.0),
        CommonFood("Strawberries", "1 cup (152 g)", F, 49, 1.0, 11.7, 0.5, fiberG = 3.0, sugarG = 7.4, sodiumMg = 2.0),
        CommonFood("Blueberries", "1 cup (148 g)", F, 84, 1.1, 21.4, 0.5, fiberG = 3.6, sugarG = 14.7, sodiumMg = 1.0),
        CommonFood("Mango", "1 cup pieces (165 g)", F, 99, 1.4, 24.7, 0.6, fiberG = 2.6, sugarG = 22.5, sodiumMg = 2.0),
        CommonFood("Watermelon", "1 cup (152 g)", F, 46, 0.9, 11.5, 0.2, fiberG = 0.6, sugarG = 9.4, sodiumMg = 2.0),
        CommonFood("Kiwi", "1 fruit (69 g)", F, 42, 0.8, 10.1, 0.4, fiberG = 2.1, sugarG = 6.2, sodiumMg = 2.0),
        CommonFood("Avocado", "½ fruit (100 g)", F, 160, 2.0, 8.5, 14.7, fiberG = 6.7, sugarG = 0.7, sodiumMg = 7.0),

        CommonFood("Boiled egg", "1 large (50 g)", P, 78, 6.3, 0.6, 5.3, sugarG = 0.6, sodiumMg = 62.0),
        CommonFood("Fried egg", "1 large (46 g)", P, 90, 6.3, 0.4, 6.8, sugarG = 0.2, sodiumMg = 95.0),
        CommonFood("Chicken breast, grilled", "100 g", P, 165, 31.0, 0.0, 3.6, sodiumMg = 74.0),
        CommonFood("Salmon, cooked", "100 g", P, 206, 22.1, 0.0, 12.4, sodiumMg = 61.0),
        CommonFood("Tuna, canned in water", "100 g drained", P, 116, 25.5, 0.0, 0.8, sodiumMg = 338.0),
        CommonFood("Lean ground beef, cooked", "100 g (90% lean)", P, 217, 26.1, 0.0, 11.7, sodiumMg = 72.0),
        CommonFood("Tofu, firm", "100 g", P, 144, 17.3, 2.8, 8.7, fiberG = 2.3, sodiumMg = 14.0),
        CommonFood("Paneer", "100 g", P, 265, 18.3, 1.2, 20.8),
        CommonFood("Dal / lentils, cooked", "1 cup (198 g)", P, 230, 17.9, 39.9, 0.8, fiberG = 15.6, sugarG = 3.6, sodiumMg = 4.0),
        CommonFood("Chickpeas, cooked", "1 cup (164 g)", P, 269, 14.5, 45.0, 4.2, fiberG = 12.5, sugarG = 7.9, sodiumMg = 11.0),
        CommonFood("Whey protein shake", "1 scoop (30 g) in water", P, 120, 24.0, 3.0, 1.5, sugarG = 1.0, sodiumMg = 50.0),

        CommonFood("White rice, cooked", "1 cup (158 g)", G, 205, 4.3, 44.5, 0.4, fiberG = 0.6, sodiumMg = 2.0),
        CommonFood("Brown rice, cooked", "1 cup (195 g)", G, 216, 5.0, 44.8, 1.8, fiberG = 3.5, sodiumMg = 10.0),
        CommonFood("Oatmeal", "1 cup cooked with water (234 g)", G, 166, 5.9, 28.1, 3.6, fiberG = 4.0, sugarG = 0.6, sodiumMg = 9.0),
        CommonFood("Pasta, cooked", "1 cup (140 g)", G, 221, 8.1, 43.2, 1.3, fiberG = 2.5, sugarG = 0.8, sodiumMg = 1.0),
        CommonFood("Whole-wheat bread", "1 slice (32 g)", G, 81, 4.0, 13.8, 1.1, fiberG = 1.9, sugarG = 1.4, sodiumMg = 146.0),
        CommonFood("White bread", "1 slice (25 g)", G, 67, 1.9, 12.7, 0.8, fiberG = 0.6, sugarG = 1.4, sodiumMg = 123.0),
        CommonFood("Roti / chapati", "1 medium (40 g)", G, 120, 3.1, 18.0, 3.7, fiberG = 2.0),
        CommonFood("Flour tortilla", "1 medium, 8\" (49 g)", G, 146, 3.9, 24.6, 3.6, fiberG = 1.7, sodiumMg = 331.0),
        CommonFood("Bagel, plain", "1 medium (98 g)", G, 270, 10.5, 53.0, 1.7, fiberG = 2.3, sugarG = 5.0, sodiumMg = 430.0),
        CommonFood("Baked potato", "1 medium (173 g)", G, 161, 4.3, 36.6, 0.2, fiberG = 3.8, sugarG = 2.0, sodiumMg = 17.0),
        CommonFood("Sweet potato, baked", "1 medium (114 g)", G, 103, 2.3, 23.6, 0.2, fiberG = 3.8, sugarG = 7.4, sodiumMg = 41.0),

        CommonFood("Greek yogurt, plain nonfat", "1 cup (170 g)", D, 100, 17.3, 6.1, 0.7, sugarG = 5.5, sodiumMg = 61.0),
        CommonFood("Milk, 2%", "1 cup (244 ml)", D, 122, 8.1, 11.7, 4.8, sugarG = 12.3, sodiumMg = 115.0),
        CommonFood("Milk, whole", "1 cup (244 ml)", D, 149, 7.7, 11.7, 7.9, sugarG = 12.3, sodiumMg = 105.0),
        CommonFood("Cheddar cheese", "1 slice (28 g)", D, 114, 7.1, 0.4, 9.4, sugarG = 0.1, sodiumMg = 176.0),
        CommonFood("Cottage cheese, 2%", "1 cup (226 g)", D, 183, 23.6, 10.8, 5.1, sugarG = 9.1, sodiumMg = 707.0),
        CommonFood("Butter", "1 tbsp (14 g)", D, 102, 0.1, 0.0, 11.5, sodiumMg = 91.0),

        CommonFood("Broccoli, cooked", "1 cup (156 g)", V, 55, 3.7, 11.2, 0.6, fiberG = 5.1, sugarG = 2.2, sodiumMg = 64.0),
        CommonFood("Carrot", "1 medium (61 g)", V, 25, 0.6, 5.8, 0.1, fiberG = 1.7, sugarG = 2.9, sodiumMg = 42.0),
        CommonFood("Mixed green salad", "2 cups, no dressing (85 g)", V, 15, 1.2, 2.9, 0.2, fiberG = 1.8, sugarG = 1.0, sodiumMg = 24.0),
        CommonFood("Olive oil", "1 tbsp (14 g)", V, 119, 0.0, 0.0, 13.5),

        CommonFood("Almonds", "1 oz / 23 nuts (28 g)", S, 164, 6.0, 6.1, 14.2, fiberG = 3.5, sugarG = 1.2),
        CommonFood("Walnuts", "1 oz (28 g)", S, 185, 4.3, 3.9, 18.5, fiberG = 1.9, sugarG = 0.7, sodiumMg = 1.0),
        CommonFood("Peanut butter", "2 tbsp (32 g)", S, 188, 8.0, 6.0, 16.0, fiberG = 1.9, sugarG = 3.0, sodiumMg = 147.0),
        CommonFood("Hummus", "2 tbsp (30 g)", S, 70, 2.0, 4.0, 5.0, fiberG = 1.2, sodiumMg = 115.0),
        CommonFood("Dark chocolate, 70–85%", "1 oz (28 g)", S, 170, 2.2, 13.0, 12.1, fiberG = 3.1, sugarG = 6.8, sodiumMg = 6.0),
        CommonFood("Popcorn, air-popped", "3 cups (24 g)", S, 93, 3.0, 18.6, 1.1, fiberG = 3.6, sugarG = 0.2, sodiumMg = 2.0),
        CommonFood("Cheese pizza", "1 slice, 14\" (107 g)", S, 285, 12.2, 35.7, 10.4, fiberG = 2.5, sugarG = 3.8, sodiumMg = 640.0),
        CommonFood("Honey", "1 tbsp (21 g)", S, 64, 0.1, 17.3, 0.0, sugarG = 17.2, sodiumMg = 1.0),

        CommonFood("Black coffee", "1 cup (240 ml)", B, 2, 0.3, 0.0, 0.0, sodiumMg = 5.0),
        CommonFood("Latte, 2% milk", "12 oz (355 ml)", B, 150, 10.0, 15.0, 6.0, sugarG = 14.0, sodiumMg = 150.0),
        CommonFood("Orange juice", "1 cup (248 ml)", B, 112, 1.7, 25.8, 0.5, fiberG = 0.5, sugarG = 20.8, sodiumMg = 2.0),
        CommonFood("Cola", "1 can (355 ml)", B, 140, 0.0, 39.0, 0.0, sugarG = 39.0, sodiumMg = 45.0),
        CommonFood("Beer, regular", "1 can (355 ml)", B, 153, 1.6, 12.6, 0.0, sodiumMg = 14.0),
        CommonFood("Red wine", "1 glass (150 ml)", B, 125, 0.1, 3.8, 0.0, sugarG = 0.9, sodiumMg = 6.0)
    )

    /** The first few to offer before the user has any saved meals of their own. */
    val starters: List<CommonFood> = listOf("Banana", "Apple", "Boiled egg", "Black coffee", "Oatmeal", "Greek yogurt, plain nonfat", "Almonds", "White rice, cooked")
        .mapNotNull { name -> all.firstOrNull { it.name == name } }
}
