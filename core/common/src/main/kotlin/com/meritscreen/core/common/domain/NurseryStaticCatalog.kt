package com.meritscreen.core.common.domain

/**
 * Built-in static catalog for early learners (Ages 3-6: Nursery, LKG, UKG).
 * Stored locally so it functions 100% offline with zero network latency,
 * and dynamically synced/overridden in real-time from Firebase DB (Firestore).
 */
object NurseryStaticCatalog {

    val DEFAULT_PLAYLISTS = listOf(
        NurseryPlaylist(
            id = "abc_phonics",
            title = "ABC Songs & Phonics",
            category = "alphabet",
            videos = listOf(
                NurseryVideo(
                    videoId = "ezmsrB59mj8",
                    title = "ABC Phonics Song for Kids",
                    maxSeconds = 180,
                    channel = "Super Simple Songs",
                ),
                NurseryVideo(
                    videoId = "BELlZKpi1Zs",
                    title = "Learn Alphabet ABC Phonics",
                    maxSeconds = 180,
                    channel = "KidsTV123",
                ),
                NurseryVideo(
                    videoId = "_UR-l3QI2nE",
                    title = "ABC Song for Children",
                    maxSeconds = 180,
                    channel = "Super Simple Songs",
                ),
            ),
        ),
        NurseryPlaylist(
            id = "counting_123",
            title = "Counting 1 to 10 Songs",
            category = "numbers",
            videos = listOf(
                NurseryVideo(
                    videoId = "DR-cfDsHCGA",
                    title = "Counting 1 to 10 Song",
                    maxSeconds = 150,
                    channel = "The Singing Walrus",
                ),
                NurseryVideo(
                    videoId = "D0Ajq682yrA",
                    title = "Number Song 1 to 20",
                    maxSeconds = 180,
                    channel = "The Singing Walrus",
                ),
                NurseryVideo(
                    videoId = "b0NHrFNZWh0",
                    title = "Five Little Monkeys Jumping On The Bed",
                    maxSeconds = 150,
                    channel = "Flickbox Kids Songs",
                ),
            ),
        ),
        NurseryPlaylist(
            id = "animal_sounds",
            title = "Animals & Sounds Songs",
            category = "animals",
            videos = listOf(
                NurseryVideo(
                    videoId = "XqZsoesa55w",
                    title = "Baby Shark Dance - Animal Songs",
                    maxSeconds = 140,
                    channel = "Pinkfong",
                ),
                NurseryVideo(
                    videoId = "9mmF8zOlh_g",
                    title = "BINGO Dog Rhyme",
                    maxSeconds = 160,
                    channel = "Super Simple Songs",
                ),
                NurseryVideo(
                    videoId = "_6HzoUcx3eo",
                    title = "Old MacDonald Had A Farm",
                    maxSeconds = 160,
                    channel = "Super Simple Songs",
                ),
            ),
        ),
        NurseryPlaylist(
            id = "colors_shapes",
            title = "Colors & Shapes Songs",
            category = "colors_shapes",
            videos = listOf(
                NurseryVideo(
                    videoId = "OEbRDtCAFdU",
                    title = "Shapes Song for Kids",
                    maxSeconds = 150,
                    channel = "The Singing Walrus",
                ),
                NurseryVideo(
                    videoId = "tkpfg-1FJLU",
                    title = "Let's Learn The Colors",
                    maxSeconds = 180,
                    channel = "ChuChu TV",
                ),
                NurseryVideo(
                    videoId = "jYAWf8Y91hA",
                    title = "I See Something Blue - Colors Song",
                    maxSeconds = 150,
                    channel = "Super Simple Songs",
                ),
            ),
        ),
        NurseryPlaylist(
            id = "nursery_rhymes",
            title = "Classic Nursery Rhymes",
            category = "rhymes",
            videos = listOf(
                NurseryVideo(
                    videoId = "yCjJyiqpAuU",
                    title = "Twinkle Twinkle Little Star",
                    maxSeconds = 140,
                    channel = "Super Simple Songs",
                ),
                NurseryVideo(
                    videoId = "e_04ZrNroTo",
                    title = "The Wheels on the Bus Go Round and Round",
                    maxSeconds = 160,
                    channel = "Cocomelon",
                ),
                NurseryVideo(
                    videoId = "71hqRT9U0wg",
                    title = "If You're Happy and You Know It",
                    maxSeconds = 150,
                    channel = "Barefoot Books",
                ),
                NurseryVideo(
                    videoId = "WX8HmogNyCY",
                    title = "Head Shoulders Knees & Toes",
                    maxSeconds = 150,
                    channel = "Super Simple Songs",
                ),
            ),
        ),
    )

    val DEFAULT_TRACKS = listOf(
        NurseryTrack(
            id = "alphabet",
            title = "Alphabet & Phonics (A to Z)",
            category = "alphabet",
            items = listOf(
                NurseryTeachItem("a_apple", "apple", "A for Apple", "Letter A", "🍎", "A is for Apple! Apples are crunchy and sweet.", "#FFEBEE"),
                NurseryTeachItem("b_ball", "ball", "B for Ball", "Letter B", "⚽", "B is for Ball! Let's bounce the ball high.", "#E3F2FD"),
                NurseryTeachItem("c_cat", "cat", "C for Cat", "Letter C", "🐱", "C is for Cat! The friendly cat says Meow.", "#FFF3E0"),
                NurseryTeachItem("d_dog", "dog", "D for Dog", "Letter D", "🐶", "D is for Dog! The happy dog barks Woof Woof.", "#FBE9E7"),
                NurseryTeachItem("e_elephant", "elephant", "E for Elephant", "Letter E", "🐘", "E is for Elephant! Elephants have long trunks.", "#EDE7F6"),
                NurseryTeachItem("f_fish", "fish", "F for Fish", "Letter F", "🐟", "F is for Fish! Fish love to swim in the water.", "#E0F7FA"),
                NurseryTeachItem("g_grapes", "grapes", "G for Grapes", "Letter G", "🍇", "G is for Grapes! Sweet purple juicy grapes.", "#F3E5F5"),
                NurseryTeachItem("h_hat", "hat", "H for Hat", "Letter H", "🎩", "H is for Hat! Put on your cozy hat.", "#FFF8E1"),
                NurseryTeachItem("i_icecream", "icecream", "I for Ice Cream", "Letter I", "🍦", "I is for Ice Cream! Cold and yummy treat.", "#FCE4EC"),
                NurseryTeachItem("j_juice", "juice", "J for Juice", "Letter J", "🧃", "J is for Juice! Healthy fruit juice for you.", "#FFF3E0"),
                NurseryTeachItem("k_kite", "kite", "K for Kite", "Letter K", "🪁", "K is for Kite! Flying high in the windy sky.", "#E1F5FE"),
                NurseryTeachItem("l_lion", "lion", "L for Lion", "Letter L", "🦁", "L is for Lion! The brave lion roars Roar!", "#FFF9C4"),
                NurseryTeachItem("m_monkey", "monkey", "M for Monkey", "Letter M", "🐵", "M is for Monkey! Monkeys love sweet yellow bananas.", "#EFEBE9"),
                NurseryTeachItem("n_nest", "nest", "N for Nest", "Letter N", "🪺", "N is for Nest! Birds build cozy nests in trees.", "#F1F8E9"),
                NurseryTeachItem("o_orange", "orange", "O for Orange", "Letter O", "🍊", "O is for Orange! Juicy bright orange fruit.", "#FFE0B2"),
                NurseryTeachItem("p_panda", "panda", "P for Panda", "Letter P", "🐼", "P is for Panda! Gentle black and white panda.", "#ECEFF1"),
                NurseryTeachItem("q_queen", "queen", "Q for Queen", "Letter Q", "👑", "Q is for Queen! The queen wears a golden crown.", "#F3E5F5"),
                NurseryTeachItem("r_rainbow", "rainbow", "R for Rainbow", "Letter R", "🌈", "R is for Rainbow! Seven beautiful colors in the sky.", "#E8EAF6"),
                NurseryTeachItem("s_sun", "sun", "S for Sun", "Letter S", "☀️", "S is for Sun! The bright sun gives us warm light.", "#FFFDE7"),
                NurseryTeachItem("t_train", "train", "T for Train", "Letter T", "🚂", "T is for Train! Chugga chugga choo choo!", "#E0F2F1"),
                NurseryTeachItem("u_umbrella", "umbrella", "U for Umbrella", "Letter U", "☂️", "U is for Umbrella! Keeps us dry in the rain.", "#E1BEE7"),
                NurseryTeachItem("v_van", "van", "V for Van", "Letter V", "🚐", "V is for Van! Driving happily down the road.", "#CFD8DC"),
                NurseryTeachItem("w_watch", "watch", "W for Watch", "Letter W", "⌚", "W is for Watch! Tick tock goes the clock.", "#E0E0E0"),
                NurseryTeachItem("x_xylophone", "xylophone", "X for Xylophone", "Letter X", "🎼", "X is for Xylophone! Ding ding ding, make music!", "#F8BBD0"),
                NurseryTeachItem("y_yellow", "yellow", "Y for Yellow", "Letter Y", "🟡", "Y is for Yellow! Bright yellow like the morning sun.", "#FFF59D"),
                NurseryTeachItem("z_zebra", "zebra", "Z for Zebra", "Letter Z", "🦓", "Z is for Zebra! Zebras have black and white stripes.", "#F5F5F5"),
            ),
        ),
        NurseryTrack(
            id = "numbers",
            title = "Numbers & Counting (1 to 10)",
            category = "numbers",
            items = listOf(
                NurseryTeachItem("num_1", "num_1", "Number 1", "One Star", "⭐", "Number 1! Look at that one shiny star shining bright.", "#FFF9C4", count = 1),
                NurseryTeachItem("num_2", "num_2", "Number 2", "Two Little Bees", "🐝", "Number 2! One, two. Two buzzing bees making honey.", "#FFF3E0", count = 2),
                NurseryTeachItem("num_3", "num_3", "Number 3", "Three Red Apples", "🍎", "Number 3! One, two, three sweet apples.", "#FFEBEE", count = 3),
                NurseryTeachItem("num_4", "num_4", "Number 4", "Four Swimming Ducks", "🦆", "Number 4! One, two, three, four happy swimming ducks.", "#E0F7FA", count = 4),
                NurseryTeachItem("num_5", "num_5", "Number 5", "Five Fingers", "🖐️", "Number 5! High five! Five fingers on your hand.", "#E8F5E9", count = 5),
                NurseryTeachItem("num_6", "num_6", "Number 6", "Six Flying Balloons", "🎈", "Number 6! One, two, three, four, five, six colorful balloons.", "#FCE4EC", count = 6),
                NurseryTeachItem("num_7", "num_7", "Number 7", "Seven Rainbow Colors", "🌈", "Number 7! Seven beautiful colors shining in the sky.", "#EDE7F6", count = 7),
                NurseryTeachItem("num_8", "num_8", "Number 8", "Eight Sweet Cherries", "🍒", "Number 8! Eight tasty cherries growing on a branch.", "#FFCDD2", count = 8),
                NurseryTeachItem("num_9", "num_9", "Number 9", "Nine Pretty Flowers", "🌸", "Number 9! Nine blooming spring flowers.", "#F8BBD0", count = 9),
                NurseryTeachItem("num_10", "num_10", "Number 10", "Ten Little Paws", "🐾", "Number 10! Let's clap together for counting all the way to ten!", "#C8E6C9", count = 10),
            ),
        ),
        NurseryTrack(
            id = "animals",
            title = "Animals & Nature",
            category = "animals",
            items = listOf(
                NurseryTeachItem("an_cow", "cow", "Cow", "Farm Animal", "🐮", "This is a cow. The friendly cow says Moo Moo!", "#EFEBE9"),
                NurseryTeachItem("an_dog", "dog", "Dog", "Loyal Friend", "🐶", "This is a dog. The puppy wags its tail and says Woof!", "#FFF3E0"),
                NurseryTeachItem("an_cat", "cat", "Cat", "Cuddly Pet", "🐱", "This is a cat. The soft kitty purrs and says Meow.", "#FFF8E1"),
                NurseryTeachItem("an_lion", "lion", "Lion", "King of Jungle", "🦁", "This is a lion. The brave lion roars loudly, Roar!", "#FFE082"),
                NurseryTeachItem("an_elephant", "elephant", "Elephant", "Gentle Giant", "🐘", "This is an elephant. Elephants are huge and have long trunks.", "#ECEFF1"),
                NurseryTeachItem("an_duck", "duck", "Duck", "Water Bird", "🦆", "This is a duck. Ducks swim in the pond and say Quack Quack!", "#E0F7FA"),
                NurseryTeachItem("an_sheep", "sheep", "Sheep", "Fluffy Wool", "🐑", "This is a sheep. The fluffy sheep says Baa Baa!", "#F5F5F5"),
                NurseryTeachItem("an_frog", "frog", "Frog", "Jumping Green", "🐸", "This is a frog. The green frog hops and says Ribbit Ribbit!", "#DCEDC8"),
            ),
        ),
        NurseryTrack(
            id = "colors_shapes",
            title = "Colors & Shapes",
            category = "colors_shapes",
            items = listOf(
                NurseryTeachItem("cs_red", "red", "Red", "Bright Color", "🔴", "This is red! Like a sweet red strawberry.", "#FFCDD2"),
                NurseryTeachItem("cs_blue", "blue", "Blue", "Sky Color", "🔵", "This is blue! Like the open sky and deep blue ocean.", "#BBDEFB"),
                NurseryTeachItem("cs_yellow", "yellow", "Yellow", "Sunny Color", "🟡", "This is yellow! Like the bright warm sunshine.", "#FFF9C4"),
                NurseryTeachItem("cs_green", "green", "Green", "Nature Color", "🟢", "This is green! Like fresh green grass and tall trees.", "#C8E6C9"),
                NurseryTeachItem("cs_circle", "circle", "Circle", "Round Shape", "⭕", "A circle is round like a ball with no sharp corners.", "#E0F2F1"),
                NurseryTeachItem("cs_square", "square", "Square", "Four Equal Sides", "🟦", "A square has four equal straight sides.", "#E1F5FE"),
                NurseryTeachItem("cs_triangle", "triangle", "Triangle", "Three Corners", "🔺", "A triangle has three pointy corners like a slice of pizza!", "#FFF3E0"),
                NurseryTeachItem("cs_star", "star", "Star", "Five Points", "⭐", "A star has five points and twinkles at night.", "#FFFDE7"),
                NurseryTeachItem("cs_heart", "heart", "Heart", "Love Shape", "❤️", "A heart is the shape of love and kindness.", "#FCE4EC"),
            ),
        ),
        NurseryTrack(
            id = "rhymes_habits",
            title = "Nursery Rhymes & Good Habits",
            category = "rhymes",
            items = listOf(
                NurseryTeachItem("rh_twinkle", "twinkle", "Twinkle Little Star", "Nursery Rhyme", "✨", "Twinkle twinkle little star, how I wonder what you are!", "#EDE7F6"),
                NurseryTeachItem("rh_bus", "wheels_bus", "Wheels on the Bus", "Nursery Rhyme", "🚌", "The wheels on the bus go round and round all through the town!", "#FFF9C4"),
                NurseryTeachItem("rh_brush", "brush_teeth", "Brush Your Teeth", "Good Habit", "🪥", "Brush your teeth in the morning and at night to keep them sparkling white!", "#E0F7FA"),
                NurseryTeachItem("rh_wash", "wash_hands", "Wash Your Hands", "Good Habit", "🧼", "Wash your hands with soap and water to keep germs away!", "#E8F5E9"),
            ),
        ),
    )

    val DEFAULT_CURRICULUM = NurseryCurriculum(
        version = 3,
        updatedAtEpochMs = 1758718600000L, // Fixed stable epoch
        playlists = DEFAULT_PLAYLISTS,
        tracks = DEFAULT_TRACKS,
    )
}
