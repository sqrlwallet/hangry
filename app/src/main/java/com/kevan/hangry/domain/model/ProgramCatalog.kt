package com.kevan.hangry.domain.model

/** Every program Hangry offers, in the order they're listed. Content is kept simple on purpose. */
object ProgramCatalog {

    private fun ex(id: String, name: String, dose: String, howTo: String, easier: String, breathing: String? = null) =
        ProgramExercise(id, name, dose, howTo, easier, breathing)

    val STRESS = Program(
        id = "stress",
        title = "Reduce stress",
        tagline = "A few minutes a day to calm your nervous system",
        intro = "Short daily habits that help your body switch out of \"on alert\" mode: slow breathing, moving outside and winding down properly. About 10 minutes a day.",
        kind = ProgramKind.MIND,
        sessionsPerWeek = 5,
        levels = listOf(
            ProgramLevel(1, "Calm basics", "Learn two quick ways to settle yourself.", listOf(
                ex("sigh", "Physiological sigh", "3 breaths",
                    "Breathe in through your nose, then take a second short sip of air on top. Let it all out slowly through your mouth. That long breath out is what calms you.",
                    "Just one slow breath in and a long breath out."),
                ex("slow_breathing", "Slow breathing", "5 minutes",
                    "Breathe at about 6 breaths a minute - in for 5, out for 5 - following Dash.",
                    "3 minutes, at whatever pace feels comfortable.", breathing = "bpm_6"),
                ex("walk", "Easy walk", "10 minutes",
                    "Walk at a relaxed pace, outside if you can. Leave your phone in your pocket.",
                    "5 minutes around the house or garden."),
                ex("grounding", "Notice five things", "1 minute",
                    "Look around and name 5 things you can see, 4 you can hear and 3 you can feel. It pulls your attention back to now.",
                    "Just 3 things you can see.")
            )),
            ProgramLevel(2, "Build the habit", "Add a proper wind-down and let your thoughts out.", listOf(
                ex("box", "Box breathing", "5 minutes",
                    "Breathe in 4, hold 4, out 4, hold 4, with Dash.",
                    "Skip the holds - just in 4, out 4.", breathing = "box_4"),
                ex("pmr", "Tense and release", "5 minutes",
                    "Lying or sitting, tense one muscle group for 5 seconds (feet, legs, belly, hands, shoulders, face), then let it go for 10. Notice the difference.",
                    "Just hands, shoulders and face."),
                ex("journal", "Brain dump", "3 minutes",
                    "Write down whatever is on your mind, however messy. Getting it on paper makes it feel smaller.",
                    "Write one sentence."),
                ex("walk", "Walk outside", "15 minutes",
                    "Relaxed pace, ideally somewhere green.",
                    "10 minutes."),
                ex("wind_down", "Screen-free wind-down", "30 minutes before bed",
                    "Put screens away and do something calm - read, stretch, a warm shower.",
                    "15 minutes.")
            )),
            ProgramLevel(3, "Steady and resilient", "Longer calm practice and a daily reset.", listOf(
                ex("slow_breathing_long", "Slow breathing", "10 minutes",
                    "About 5 breaths a minute, with Dash. Let your belly rise, not your shoulders.",
                    "6 breaths a minute for 5 minutes.", breathing = "bpm_5"),
                ex("gratitude", "Three good things", "2 minutes",
                    "Write down 3 things that went well today and why.",
                    "One good thing."),
                ex("nature", "Time in nature", "20 minutes",
                    "A walk in a park, by water or among trees.",
                    "10 minutes outside."),
                ex("plan", "Plan tomorrow", "3 minutes",
                    "In the evening, write tomorrow's top 3 things so they're not circling in your head at night.",
                    "Just the one most important thing."),
                ex("digital_sunset", "Digital sunset", "1 hour before bed",
                    "No news, email or social media for the last hour of the day.",
                    "30 minutes.")
            ))
        ),
        sessionsToAdvance = 7
    )

    val FOCUS = Program(
        id = "focus",
        title = "Increase focus",
        tagline = "Train your attention like a muscle",
        intro = "Focus gets better with practice and fewer interruptions. Start with one short distraction-free block a day and build up to real deep work.",
        kind = ProgramKind.MIND,
        sessionsPerWeek = 5,
        levels = listOf(
            ProgramLevel(1, "One clear block", "One short stretch of single-tasking.", listOf(
                ex("settle", "Settle in", "2 minutes",
                    "Before you start, a couple of minutes of slow breathing with Dash.",
                    "5 slow breaths.", breathing = "bpm_6"),
                ex("phone_away", "Phone in another room", "during the block",
                    "Not face-down on the desk - out of sight. Just seeing it pulls your attention.",
                    "In a drawer, on silent."),
                ex("block", "Focus block", "25 minutes",
                    "Pick one task. Work on only that until the timer goes. If your mind wanders, gently bring it back.",
                    "15 minutes."),
                ex("break", "Move break", "5 minutes",
                    "Stand up, walk, get water. No scrolling.",
                    "Stand and stretch for 2 minutes.")
            )),
            ProgramLevel(2, "Two blocks", "Two blocks, planned in advance.", listOf(
                ex("one_thing", "Name the task", "1 minute",
                    "Write down exactly what you'll work on before you start, e.g. \"draft the first page\".",
                    "Say it out loud."),
                ex("notifications", "Notifications off", "during blocks",
                    "Use Do Not Disturb on your phone and computer.",
                    "Silence just your phone."),
                ex("blocks", "Two focus blocks", "2 × 25 minutes",
                    "With a 5-minute move break between them.",
                    "2 × 15 minutes."),
                ex("far_look", "Rest your eyes", "20 seconds per block",
                    "Every block, look at something far away (out a window) for 20 seconds.",
                    "Close your eyes for 20 seconds."),
                ex("daylight", "Morning daylight", "10 minutes",
                    "Get outside in daylight within an hour of waking. It sets your body clock for alert days and better sleep.",
                    "5 minutes by an open window.")
            )),
            ProgramLevel(3, "Deep work", "Longer blocks and a focus-friendly day.", listOf(
                ex("plan", "Plan tomorrow's top 3", "3 minutes, the evening before",
                    "Decide the night before what your deep-work blocks are for.",
                    "Just the first block."),
                ex("no_phone_morning", "Phone-free first 30 minutes", "each morning",
                    "Don't check messages or news for the first half hour after waking.",
                    "15 minutes."),
                ex("start_breath", "Box breathing to start", "3 minutes",
                    "Steady your attention before the first block.",
                    "1 minute.", breathing = "box_4"),
                ex("deep_blocks", "Deep work blocks", "3 × 45 minutes",
                    "Single task, notifications off, phone away. Walk for 10 minutes between blocks.",
                    "2 × 45 minutes.")
            ))
        ),
        sessionsToAdvance = 7
    )

    val MOBILITY = Program(
        id = "mobility",
        title = "Increase mobility",
        tagline = "10 minutes a day for hips, spine, ankles and shoulders",
        intro = "Gentle daily movement to keep your joints moving well: easier getting up off the floor, reaching overhead and turning to look behind you.",
        kind = ProgramKind.BODY,
        sessionsPerWeek = 5,
        countsAsMobility = true,
        levels = listOf(
            ProgramLevel(1, "Loosen up", "Easy movements through every joint.", listOf(
                ex("cat_cow", "Cat-cow", "10 slow reps",
                    "On hands and knees, round your back up like a cat, then let your belly drop and look up. Move with your breath.",
                    "Sit on a chair and round and arch your back."),
                ex("hip_circles", "Hip circles", "10 each way",
                    "Standing, hands on hips, draw big slow circles with your hips.",
                    "Smaller circles, holding a chair."),
                ex("open_book", "Open book", "8 each side",
                    "Lie on your side, knees bent, arms out in front. Open the top arm across to the other side, following it with your eyes, then close.",
                    "Only open halfway."),
                ex("ankle_rocks", "Ankle rocks", "10 each side",
                    "Kneel in a lunge facing a wall. Keeping the front heel down, rock your knee toward the wall and back.",
                    "Stand and rock onto your toes and heels."),
                ex("neck", "Neck turns", "5 each way",
                    "Slowly turn your head to look over each shoulder, then tip each ear toward your shoulder.",
                    "Smaller, slower movements.")
            )),
            ProgramLevel(2, "More range", "Bigger stretches that link hips and spine.", listOf(
                ex("worlds_greatest", "World's greatest stretch", "5 each side",
                    "Step into a long lunge, put the same-side hand down inside your front foot, then reach the other arm up to the ceiling.",
                    "Back knee down on a cushion."),
                ex("ninety_ninety", "90/90 hip switches", "8 each side",
                    "Sit with both knees bent to one side at right angles, then rock both knees over to the other side. Stay tall.",
                    "Lean back on your hands."),
                ex("squat_hold", "Supported deep squat", "30 seconds × 2",
                    "Hold a door frame or counter and sit down into a deep squat. Let your hips sink and breathe.",
                    "Squat onto a low stool."),
                ex("thread_needle", "Thread the needle", "8 each side",
                    "On hands and knees, slide one arm under your body along the floor, twisting your upper back, then reach it up to the ceiling.",
                    "Just the slide under."),
                ex("hamstring", "Hamstring floss", "10 each side",
                    "Lie on your back, hold behind one thigh, and slowly straighten and bend the knee.",
                    "Keep a bigger bend in the knee.")
            )),
            ProgramLevel(3, "Move freely", "Deeper positions with control.", listOf(
                ex("cossack", "Cossack squat", "6 each side",
                    "Wide stance. Shift your weight and sit into one leg while the other stays straight, toes up. Hold something if you need to.",
                    "Only go halfway down, holding a chair."),
                ex("squat_hold_long", "Deep squat hold", "60 seconds × 2",
                    "Unsupported if you can, heels on a folded towel if they lift.",
                    "Hold on to something."),
                ex("towel_pass", "Towel pass-throughs", "10 reps",
                    "Hold a towel wide with straight arms and slowly lift it overhead and behind you, then back.",
                    "Hold the towel wider, or stop overhead."),
                ex("couch_stretch", "Couch stretch", "60 seconds each side",
                    "Kneel with your back shin up against a couch or wall and the other foot forward. Squeeze your glute and stay tall.",
                    "Knee further from the wall."),
                ex("ninety_lift", "90/90 with lift-offs", "5 each side",
                    "In the 90/90 position, lift your back foot off the floor for a second, then lower.",
                    "90/90 switches instead.")
            ))
        )
    )

    val SHOULDER = Program(
        id = "shoulder",
        title = "Fix your shoulders",
        tagline = "Healthier, stronger shoulders from desk to overhead",
        intro = "For stiff, achy or weak shoulders: gentle movement first, then the small muscles that keep the shoulder stable, then strength overhead.",
        kind = ProgramKind.BODY,
        sessionsPerWeek = 3,
        countsAsMobility = true,
        strengthFromLevel = 3,
        equipment = "Two water bottles or light weights, a towel, a wall.",
        extraSafety = "See a doctor if you can't lift your arm, have pain after a fall, or have pain that wakes you every night.",
        levels = listOf(
            ProgramLevel(1, "Gentle motion", "Get the shoulder moving without strain.", listOf(
                ex("pendulum", "Pendulum swings", "30 seconds each arm",
                    "Lean forward with one hand on a table and let the other arm hang. Gently swing it in small circles and side to side.",
                    "Smaller swings."),
                ex("wall_slide", "Wall slides", "2 × 8",
                    "Stand facing a wall with forearms on it. Slide your arms up as high as is comfortable, then back down.",
                    "Slide only to shoulder height."),
                ex("scap_squeeze", "Shoulder blade squeezes", "2 × 10",
                    "Sit or stand tall and squeeze your shoulder blades back and down, hold 3 seconds, relax.",
                    "Hold for 1 second."),
                ex("side_er", "Side-lying rotation", "2 × 10 each arm",
                    "Lie on your side, top elbow bent at 90° and tucked to your ribs. Rotate the forearm up toward the ceiling and back down.",
                    "A smaller range."),
                ex("doorway", "Doorway chest stretch", "30 seconds",
                    "Forearms on a door frame at shoulder height, step through gently until you feel the chest stretch.",
                    "One arm at a time, gentler.")
            )),
            ProgramLevel(2, "Stability", "Strengthen the small muscles that hold the shoulder in place.", listOf(
                ex("wall_angel", "Wall angels", "2 × 10",
                    "Back, head and bottom against a wall, arms in a goalpost shape. Slide them up and down, keeping contact with the wall as best you can.",
                    "Step your feet further from the wall."),
                ex("ytw", "Y-T-W raises", "2 × 6 each letter",
                    "Lie face down (or bend over, chest supported). Lift your arms into a Y, then a T, then a W, thumbs up, squeezing your shoulder blades.",
                    "Only the T."),
                ex("er_bottle", "Rotation with a bottle", "2 × 10 each arm",
                    "Side-lying rotation holding a water bottle.",
                    "No bottle."),
                ex("towel_apart", "Towel pull-aparts", "3 × 10",
                    "Hold a towel in front of you at shoulder height and pull it apart hard for 3 seconds, shoulder blades squeezing.",
                    "Lighter pull."),
                ex("supported_hang", "Supported hang", "3 × 15 seconds",
                    "Hold a bar or sturdy door frame overhead with your feet still on the floor and let your shoulders gently stretch.",
                    "Hands on a high shelf, lean away.")
            )),
            ProgramLevel(3, "Strength overhead", "Carry that stability into real strength.", listOf(
                ex("pushup_plus", "Push-up plus", "3 × 10",
                    "In a wall or counter push-up position, keep arms straight and push the floor away to spread your shoulder blades, then relax.",
                    "Against a wall."),
                ex("ytw_weight", "Y-T-W with bottles", "3 × 8 each letter",
                    "As before, holding water bottles.",
                    "No bottles."),
                ex("overhead_press", "Overhead press with bottles", "3 × 10",
                    "Standing tall, press the bottles overhead without arching your back.",
                    "One arm at a time."),
                ex("side_plank", "Side plank", "3 × 20 seconds each side",
                    "On your elbow and the side of your feet (or knees), body in a straight line.",
                    "On your knees."),
                ex("hang", "Dead hang", "3 × 20 seconds",
                    "Hang from a bar with your feet off the floor if you can, or partly supported.",
                    "Keep your feet on the floor.")
            ))
        )
    )

    val BACK = Program(
        id = "back",
        title = "Fix back issues",
        tagline = "A stronger, calmer lower back",
        intro = "For everyday back stiffness and aches: walk more, move your spine gently, then build a strong core and hips that take load off your back.",
        kind = ProgramKind.BODY,
        sessionsPerWeek = 4,
        countsAsMobility = true,
        strengthFromLevel = 3,
        extraSafety = "Get medical help promptly if back pain comes with numbness or weakness in the legs, loss of bladder or bowel control, fever, unexplained weight loss, or follows a fall or accident.",
        levels = listOf(
            ProgramLevel(1, "Gentle start", "Walking and easy movement - motion is lotion for your back.", listOf(
                ex("walk", "Walk", "10 minutes",
                    "At a comfortable pace. Walking is one of the best things for most back pain.",
                    "5 minutes, twice a day."),
                ex("pelvic_tilt", "Pelvic tilts", "2 × 10",
                    "Lie on your back, knees bent. Gently flatten your lower back into the floor, then relax.",
                    "A smaller movement."),
                ex("knee_chest", "Knee to chest", "30 seconds each side",
                    "Lying on your back, hug one knee gently toward your chest.",
                    "Hold behind the thigh instead of the knee."),
                ex("cat_cow", "Cat-cow", "10 slow reps",
                    "On hands and knees, gently round and arch your back.",
                    "Seated on a chair."),
                ex("bridge", "Glute bridge", "2 × 10",
                    "Lying on your back, knees bent, squeeze your glutes and lift your hips, then lower slowly.",
                    "Lift only a little.")
            )),
            ProgramLevel(2, "Core control", "Teach your core to hold your spine steady.", listOf(
                ex("walk", "Walk", "15 minutes",
                    "A little brisker.",
                    "10 minutes."),
                ex("bird_dog", "Bird dog", "2 × 6 each side, hold 5 s",
                    "On hands and knees, reach one arm forward and the opposite leg back, keeping your back flat. Hold, then switch.",
                    "Just the leg, or just the arm."),
                ex("dead_bug", "Dead bug", "2 × 8",
                    "On your back, arms up, knees bent at 90°. Lower the opposite arm and leg toward the floor while keeping your lower back down.",
                    "Move only the legs."),
                ex("bridge", "Glute bridge", "3 × 12",
                    "Pause 2 seconds at the top.",
                    "2 × 10."),
                ex("side_plank_knees", "Side plank on knees", "3 × 15 seconds each side",
                    "On your elbow and knees, hips lifted in a straight line.",
                    "10 seconds."),
                ex("hip_flexor", "Kneeling hip flexor stretch", "30 seconds each side",
                    "Kneel on one knee, squeeze that glute and shift your hips forward gently.",
                    "Go gentler.")
            )),
            ProgramLevel(3, "Strong back", "Build the strength that protects your back for good.", listOf(
                ex("walk", "Walk", "20 minutes",
                    "Brisk.",
                    "15 minutes."),
                ex("bird_dog_long", "Bird dog", "3 × 8 each side",
                    "Slow and controlled, hold 5 seconds.",
                    "2 × 6."),
                ex("side_plank", "Side plank", "3 × 20 seconds each side",
                    "On your elbow and feet.",
                    "On your knees."),
                ex("single_bridge", "Single-leg bridge", "2 × 8 each side",
                    "One foot on the floor, the other leg straight. Lift your hips level.",
                    "Two-leg bridge."),
                ex("hinge", "Hip hinge with a broom", "2 × 10",
                    "Hold a broom along your back touching your head, upper back and tailbone. Push your hips back and tip forward, keeping all three touching, then stand up.",
                    "A smaller bend."),
                ex("carry", "Suitcase carry", "3 × 30 seconds each side",
                    "Walk holding a heavy bag in one hand, standing tall without leaning.",
                    "A lighter bag.")
            ))
        )
    )

    val PULL_UP = Program(
        id = "pull_up",
        title = "Your first pull-up",
        tagline = "From hanging on to pulling your chin over the bar",
        intro = "Build grip, shoulder and back strength step by step until you can do a full pull-up. Most people get there in a couple of months.",
        kind = ProgramKind.BODY,
        sessionsPerWeek = 3,
        strengthFromLevel = 1,
        equipment = "A pull-up bar (or a sturdy bar), and a sturdy table for rows.",
        levels = listOf(
            ProgramLevel(1, "Hang on", "Grip, shoulder and core foundations.", listOf(
                ex("dead_hang", "Dead hang", "3 × 10-20 seconds",
                    "Hang from the bar with straight arms, shoulders active (not shrugged into your ears).",
                    "Keep your toes on a box or the floor."),
                ex("scap_pull", "Scapular pulls", "2 × 5",
                    "Hanging with straight arms, pull your shoulder blades down so you rise an inch, then lower.",
                    "Feet supported."),
                ex("table_row", "Table rows", "3 × 6",
                    "Lie under a sturdy table, grip the edge, knees bent, and pull your chest up to it.",
                    "Stand and row against a door frame, leaning back."),
                ex("hollow", "Hollow hold", "3 × 15 seconds",
                    "On your back, lower back pressed down, lift your shoulders and legs slightly off the floor.",
                    "Knees bent.")
            )),
            ProgramLevel(2, "Build the pull", "Start owning the top and the way down.", listOf(
                ex("dead_hang_long", "Dead hang", "3 × 30 seconds",
                    "Feet off the floor.",
                    "20 seconds."),
                ex("table_row_straight", "Rows, straighter body", "3 × 8",
                    "Table or low-bar rows with your legs straighter.",
                    "Knees bent."),
                ex("top_hold", "Top hold", "3 × 5-10 seconds",
                    "Jump or step up so your chin is over the bar, and hold it there.",
                    "Hold with arms at 90°."),
                ex("negatives", "Negative pull-ups", "3 × 3",
                    "Jump or step up to the top, then lower yourself as slowly as you can (aim for 3-5 seconds).",
                    "2 × 3, faster lowering.")
            )),
            ProgramLevel(3, "Almost there", "Longer negatives and partial pulls.", listOf(
                ex("negatives_slow", "Slow negatives", "3 × 5, 5 seconds down",
                    "Control the whole way to straight arms.",
                    "3 × 3."),
                ex("flexed_hang", "Flexed-arm hang", "3 × 15 seconds",
                    "Hold with your chin at or above the bar.",
                    "10 seconds."),
                ex("partials", "Top-half pull-ups", "3 × 3",
                    "From arms at 90°, pull up until your chin clears the bar.",
                    "Negatives only."),
                ex("rows", "Rows", "3 × 10",
                    "Body as straight as you can manage.",
                    "Knees bent.")
            )),
            ProgramLevel(4, "First pull-up", "Put it together.", listOf(
                ex("attempts", "Pull-up attempts", "3 attempts, 2 min rest",
                    "From a dead hang, pull until your chin clears the bar. Stop each attempt when your form breaks.",
                    "Jump-assisted pull-ups."),
                ex("negatives", "Negatives", "2 × 3",
                    "Slow and controlled.",
                    "2 × 2."),
                ex("rows", "Rows", "3 × 10",
                    "Straight body.",
                    "Knees bent."),
                ex("dead_hang", "Dead hang", "2 × 30 seconds",
                    "Finish with a hang to build grip.",
                    "20 seconds.")
            ))
        )
    )

    val PUSH_UP = Program(
        id = "push_up",
        title = "Your first push-up",
        tagline = "From the wall to the floor",
        intro = "Lower the angle a little at a time - wall, counter, sofa, floor - until you can do full push-ups with good form.",
        kind = ProgramKind.BODY,
        sessionsPerWeek = 3,
        strengthFromLevel = 1,
        levels = listOf(
            ProgramLevel(1, "Wall", "Learn the movement standing up.", listOf(
                ex("wall_pushup", "Wall push-ups", "3 × 10",
                    "Hands on a wall at chest height, body straight like a plank. Bend your elbows to bring your chest to the wall, then push away.",
                    "Stand closer to the wall."),
                ex("plank_knees", "Plank", "3 × 20 seconds",
                    "On your forearms and toes (or knees), body in a straight line, belly tight.",
                    "On your knees."),
                ex("scap_pushup", "Wall shoulder-blade push-ups", "2 × 8",
                    "In a wall push-up position with straight arms, let your chest sink between your shoulder blades, then push the wall away.",
                    "Smaller movement.")
            )),
            ProgramLevel(2, "Counter", "A lower angle means more of your weight.", listOf(
                ex("incline_high", "Counter push-ups", "3 × 8",
                    "Hands on a kitchen counter or sturdy table. Chest to the edge, then push up, body straight.",
                    "Back to the wall."),
                ex("plank", "Plank", "3 × 30 seconds",
                    "On your toes if you can.",
                    "On your knees."),
                ex("slow_lower", "Slow lowering", "2 × 5",
                    "Counter push-ups, taking 3 seconds to lower.",
                    "Normal speed.")
            )),
            ProgramLevel(3, "Low incline", "Nearly on the floor.", listOf(
                ex("incline_low", "Sofa or stair push-ups", "3 × 8",
                    "Hands on a sofa seat, low bench or the third stair.",
                    "Counter height."),
                ex("knee_pushup", "Knee push-ups", "3 × 8",
                    "On the floor from your knees, hips straight (not bent at the waist).",
                    "3 × 5."),
                ex("floor_negatives", "Floor negatives", "3 × 3",
                    "From the top of a full push-up, lower yourself slowly to the floor over 3-5 seconds. Reset on your knees.",
                    "Knee negatives.")
            )),
            ProgramLevel(4, "Floor", "Full push-ups.", listOf(
                ex("floor_pushup", "Full push-ups", "3 sets of as many good reps as you can (1-5)",
                    "Hands just wider than shoulders, body straight, chest to about a fist from the floor.",
                    "Finish each set on your knees."),
                ex("floor_negatives", "Negatives", "2 × 3",
                    "Slow lowering.",
                    "2 × 2."),
                ex("plank_long", "Plank", "3 × 45 seconds",
                    "Straight line, steady breathing.",
                    "30 seconds.")
            ))
        )
    )

    val KNEES = Program(
        id = "knees",
        title = "Knees over toes",
        tagline = "Stronger knees, step by step",
        intro = "Knees go past your toes every day - on stairs, sitting down, getting up. This trains that position gently so your feet, shins, knees and hips get stronger through their full range. About 10-15 minutes, 3 times a week, no equipment.",
        kind = ProgramKind.BODY,
        sessionsPerWeek = 3,
        countsAsMobility = true,
        strengthFromLevel = 2,
        credit = "Inspired by the knees-over-toes approach popularised by Ben Patrick (ATG). Hangry isn't affiliated with ATG.",
        levels = listOf(
            ProgramLevel(1, "Foundations", "Wake up your feet, shins and knees with small, easy movements.", listOf(
                ex("backward_walk", "Backward walking", "3 minutes",
                    "Walk backwards slowly in a clear hallway or on a treadmill at its lowest speed, landing on the balls of your feet. Look over your shoulder now and then.",
                    "Walk for 1 minute and hold a wall or rail."),
                ex("tib_raise", "Wall tibialis raises", "2 × 15",
                    "Lean your back on a wall with your heels a foot or so in front of you. Lift your toes up toward your shins, then lower slowly.",
                    "Stand closer to the wall, or do 2 × 8."),
                ex("calf_raise", "Calf raises", "2 × 15",
                    "Stand tall holding a counter. Rise onto your toes, pause, and lower slowly for 2-3 seconds.",
                    "Do 2 × 8, or sit and do them seated."),
                ex("supported_split", "Supported split squat", "2 × 6 each leg",
                    "Hold a chair or wall. Step one foot forward and gently lower your back knee, letting the front knee drift forward only as far as feels fine. Push back up.",
                    "Go only a few inches down. Range comes with time."),
                ex("couch_stretch", "Kneeling hip flexor stretch", "30 seconds each side",
                    "Kneel on one knee on a cushion, other foot in front. Squeeze your glute and shift your hips forward until you feel the front of the hip stretch.",
                    "Keep your torso upright and go gentler.")
            )),
            ProgramLevel(2, "Building range", "A little more range and single-leg control.", listOf(
                ex("backward_walk", "Backward walking", "5 minutes",
                    "Walk backwards at an easy pace, knees staying soft and bent.",
                    "3 minutes, holding a rail."),
                ex("tib_raise", "Wall tibialis raises", "2 × 20",
                    "Heels a little further from the wall to make it harder. Slow on the way down.",
                    "Heels closer to the wall."),
                ex("single_calf", "Single-leg calf raises", "2 × 10 each leg",
                    "Hold a counter and rise onto the toes of one foot, then lower slowly.",
                    "Both legs, with more weight on one."),
                ex("patrick_step", "Patrick step (low)", "2 × 10 each leg",
                    "Stand on a low step on one leg, heel slightly raised. Slowly bend that knee forward past your toes to tap the other heel on the floor in front, then stand back up.",
                    "Use a lower step and hold a rail."),
                ex("elevated_split", "Split squat, front foot raised", "2 × 8 each leg",
                    "Put your front foot on a step and lower your back knee toward the floor, letting the front knee travel forward. Hold something for balance.",
                    "Use a higher step so you go less deep."),
                ex("couch_stretch", "Couch stretch", "45 seconds each side",
                    "Kneel with your back shin up against a couch or wall behind you and the other foot forward. Squeeze your glute and stay tall.",
                    "Move your knee further from the wall.")
            )),
            ProgramLevel(3, "Full range", "Take the knee all the way forward, still with support.", listOf(
                ex("backward_walk", "Backward walking", "5 minutes",
                    "A bit faster, or up a slight slope.",
                    "Keep it flat and easy."),
                ex("tib_raise", "Wall tibialis raises", "3 × 20",
                    "Heels far from the wall, 2-second pause at the top.",
                    "2 × 20 with heels closer."),
                ex("kot_calf", "Knee-forward calf raises", "2 × 12 each leg",
                    "On one leg, push your knee forward over your toes and hold it there while you rise onto your toes and lower. Hold a counter.",
                    "Both legs at once."),
                ex("patrick_step", "Patrick step", "2 × 15 each leg",
                    "On a slightly higher step, slow and controlled.",
                    "Back to the lower step."),
                ex("atg_split", "Deep split squat", "2 × 8 each leg",
                    "Long stance, front foot flat. Let your front knee travel well past your toes as your back knee drops toward the floor. Light support with a hand.",
                    "Front foot on a step again."),
                ex("elephant_walk", "Elephant walk", "2 × 10",
                    "Fold forward with your hands on the floor (or a step). Bend one knee while straightening the other, alternating, to stretch the hamstrings.",
                    "Hands on a chair seat instead of the floor.")
            )),
            ProgramLevel(4, "Strong knees", "Full-range strength with less support. Keep it smooth.", listOf(
                ex("backward_walk", "Backward walking", "5 minutes brisk",
                    "Brisk, or up a slope, knees bent the whole time.",
                    "Easy pace on the flat."),
                ex("tib_raise", "Tibialis raises", "3 × 25",
                    "Slow and controlled, heels far from the wall.",
                    "3 × 15."),
                ex("kot_calf", "Knee-forward single-leg calf raises", "3 × 10 each leg",
                    "Knee pushed forward past the toes, rise and lower on one leg.",
                    "Hold a counter with both hands."),
                ex("poliquin_step", "Poliquin step-up", "2 × 12 each leg",
                    "Stand on a step on one leg with the heel slightly raised, knee travelling forward. Lower the other foot to lightly tap the floor, then press back up.",
                    "Patrick step instead."),
                ex("atg_split", "Deep split squat", "3 × 8 each leg",
                    "No support if you can. Knee forward, back knee down, torso tall.",
                    "Use a hand for balance."),
                ex("deep_squat", "Deep squat hold", "3 × 30 seconds",
                    "Sit into a full squat and hold it, heels on a folded towel if they lift. Hold something in front of you if needed.",
                    "Squat onto a low stool and hold.")
            ))
        )
    )

    val ALL: List<Program> = listOf(STRESS, FOCUS, MOBILITY, SHOULDER, BACK, PULL_UP, PUSH_UP, KNEES)

    fun byId(id: String?): Program? = ALL.firstOrNull { it.id == id }
}
