```markdown
# 🔧 Calculators — Mechanical Workshop Billing Application

A Tamil/English voice-enabled billing app for mechanical workshops. Speak or type a machine part name, quantity, and amount in Tamil, English, or mixed speech — the app parses it, matches it to a part image, and builds a bill instantly.

## Features

- 🎙️ **Bilingual voice billing** — speak product entries in Tamil, English, or mixed (Tanglish) speech using Android's native speech recognizer
- 🧠 **Smart text parsing** — automatically extracts product name, quantity, and amount from free-form transcripts (`AmountExtractor`, `QuantityExtractor`, `ProductParser`, `LanguageDetector`)
- 🏷️ **Product dictionary** — extensible list of mechanical machine parts and calibration items, with fuzzy same-script matching; supports runtime-registered custom parts
- 🖼️ **Mechanical part image matching** — auto-matches spoken/typed part names to bundled part images, with manual confirmation, replacement, and device-gallery selection
- 🧮 **Accurate billing math** — `BigDecimal`-based line totals and grand totals, with optional unit-price × quantity calculation and ₹ currency formatting
- 🧾 **Bill history** — saves Draft and Finalized bills locally via Room, with full item-level detail preserved
- 🖨️ **Receipt preview, print & share** — generate a PDF receipt, print it, or share it directly from the app
- 🎨 **Modern UI** — built entirely with Jetpack Compose and Material 3

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Architecture:** MVVM (ViewModel + StateFlow/UiState)
- **Persistence:** Room (`BillEntity`, `BillItemEntity`)
- **Voice:** Android `SpeechRecognizer` API
- **Async:** Kotlin Coroutines
- **Build:** Gradle (Kotlin DSL), KSP
- **Optional:** Gemini API, Firebase (Auth/Firestore scaffolding present, disabled by default)

## Setup / Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/gopigopi-dev/Calculators-Mechanical-Workshop-Billing-Application-.git
   cd Calculators-Mechanical-Workshop-Billing-Application-
   ```
2. **Open in Android Studio** (Giraffe or newer recommended) and let Gradle sync.
3. **(Optional) Configure the Gemini API key** — copy `.env.example` to `.env` and uncomment/set:
   ```
   GEMINI_API_KEY=your_api_key_here
   ```
4. **Build & run**
   ```bash
   ./gradlew assembleDebug
   ```
   or click **Run ▶** in Android Studio on a device/emulator (min SDK 24).
5. Grant the **microphone (RECORD_AUDIO)** permission when prompted, to use voice billing.

## Usage

1. Tap the microphone button and speak a part name with quantity/amount (Tamil, English, or mixed), or type it manually.
2. The app parses the transcript into a product entry and attempts to match a part image.
3. Confirm or adjust the matched image, quantity, and amount as needed.
4. Add more entries — the grand total updates automatically.
5. Save the bill as a **Draft** or **Finalize** it, then preview, print, or share the receipt as a PDF.
6. View past bills anytime from the **Bill History** screen.

## Project Structure

```
app/src/main/java/com/example/calculators/
├── data/            # Room entities, DAOs, repository (bills & bill items)
├── image/           # Mechanical part image matching & collection management
├── model/           # Product model and billing calculation logic
├── presentation/     # Compose screens & UI components
├── textprocessing/   # Tamil/English transcript parsing, tokenizing, normalization
├── theme/            # App theming
├── viewmodel/        # BillingViewModel, BillHistoryViewModel
└── voice/            # Speech recognition manager & listener
```

## License

This project is licensed under the MIT License.
```

### 2. Commit Message

```text
docs: add comprehensive project README
```

### 3. Execution Script (Bash)

```bash
# Run inside your cloned project folder: bash publish.sh
set -e

# 1. Confirm GitHub CLI is authenticated
gh auth status

# 2. Repo already has .git, skip init
[ -d .git ] || git init

# 3. Create a descriptive branch for this update
git checkout -b docs/readme

# 4. .gitignore already exists and excludes .env/secrets — no changes needed

# 5. Write README.md via quoted heredoc (prevents shell expansion of $ , ` , " )
cat > README.md << 'EOF'
# 🔧 Calculators — Mechanical Workshop Billing Application

A Tamil/English voice-enabled billing app for mechanical workshops. Speak or type a machine part name, quantity, and amount in Tamil, English, or mixed speech — the app parses it, matches it to a part image, and builds a bill instantly.

## Features

- 🎙️ **Bilingual voice billing** — speak product entries in Tamil, English, or mixed (Tanglish) speech using Android's native speech recognizer
- 🧠 **Smart text parsing** — automatically extracts product name, quantity, and amount from free-form transcripts (`AmountExtractor`, `QuantityExtractor`, `ProductParser`, `LanguageDetector`)
- 🏷️ **Product dictionary** — extensible list of mechanical machine parts and calibration items, with fuzzy same-script matching; supports runtime-registered custom parts
- 🖼️ **Mechanical part image matching** — auto-matches spoken/typed part names to bundled part images, with manual confirmation, replacement, and device-gallery selection
- 🧮 **Accurate billing math** — `BigDecimal`-based line totals and grand totals, with optional unit-price × quantity calculation and ₹ currency formatting
- 🧾 **Bill history** — saves Draft and Finalized bills locally via Room, with full item-level detail preserved
- 🖨️ **Receipt preview, print & share** — generate a PDF receipt, print it, or share it directly from the app
- 🎨 **Modern UI** — built entirely with Jetpack Compose and Material 3

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Architecture:** MVVM (ViewModel + StateFlow/UiState)
- **Persistence:** Room (`BillEntity`, `BillItemEntity`)
- **Voice:** Android `SpeechRecognizer` API
- **Async:** Kotlin Coroutines
- **Build:** Gradle (Kotlin DSL), KSP
- **Optional:** Gemini API, Firebase (Auth/Firestore scaffolding present, disabled by default)

## Setup / Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/gopigopi-dev/Calculators-Mechanical-Workshop-Billing-Application-.git
   cd Calculators-Mechanical-Workshop-Billing-Application-
   ```
2. **Open in Android Studio** (Giraffe or newer recommended) and let Gradle sync.
3. **(Optional) Configure the Gemini API key** — copy `.env.example` to `.env` and uncomment/set:
   ```
   GEMINI_API_KEY=your_api_key_here
   ```
4. **Build & run**
   ```bash
   ./gradlew assembleDebug
   ```
   or click **Run ▶** in Android Studio on a device/emulator (min SDK 24).
5. Grant the **microphone (RECORD_AUDIO)** permission when prompted, to use voice billing.

## Usage

1. Tap the microphone button and speak a part name with quantity/amount (Tamil, English, or mixed), or type it manually.
2. The app parses the transcript into a product entry and attempts to match a part image.
3. Confirm or adjust the matched image, quantity, and amount as needed.
4. Add more entries — the grand total updates automatically.
5. Save the bill as a **Draft** or **Finalize** it, then preview, print, or share the receipt as a PDF.
6. View past bills anytime from the **Bill History** screen.

## Project Structure

```
app/src/main/java/com/example/calculators/
├── data/            # Room entities, DAOs, repository (bills & bill items)
├── image/           # Mechanical part image matching & collection management
├── model/           # Product model and billing calculation logic
├── presentation/     # Compose screens & UI components
├── textprocessing/   # Tamil/English transcript parsing, tokenizing, normalization
├── theme/            # App theming
├── viewmodel/        # BillingViewModel, BillHistoryViewModel
└── voice/            # Speech recognition manager & listener
```

## License

This project is licensed under the MIT License.
EOF
