<div align="center">

  <img src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png" alt="Reality App Logo" width="150"/>

  # Reality Engine
  **The Intelligent Life OS | V1.0.6 Neural Core**

  [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
  [![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
  [![Status](https://img.shields.io/badge/Status-Active-success.svg)]()

  *Stop managing your life. Start commanding it.*

</div>

## Overview
Reality is a comprehensive "military-grade productivity operating system" for Android. It enforces focus, orchestrates end-of-day reflections, and prevents digital distractions using an ultra-strict architectural layer.

The app follows a unique Open-Source/Pro model:
- The code is **100% Free and Open Source**.
- An optimized, ready-to-install **Pro APK** requires a one-time payment to support the developer.

## Core Architecture
- **Tapasya (Deep Focus):** Distraction-free focus blocks.
- **Nightly Protocol:** An automated end-of-day sequence that collects reflections, generates PDFs, and pushes them to Google Drive.
- **Strict Mode OS:** DeviceAdmin and Overlay-powered blockers that make bypassing time limits physically impossible without math equations.
- **Neural Assistant:** A locally integrated "Bring Your Own Key" (BYOK) AI chat interface.
- **Health Connect:** Physical metric synthesis layered over productivity stats.

## Development & Build Instructions
This project requires Android Studio Ladybug or newer.

```bash
# Clone the repository
git clone https://github.com/Pawan-Washudev/reality-app.git

# Set up local properties (Create local.properties in root)
echo "WEB_CLIENT_ID=your_client_id" >> local.properties
echo "DEFAULT_CLIENT_ID=your_desktop_client_id" >> local.properties
echo "DEFAULT_CLIENT_SECRET=your_desktop_client_secret" >> local.properties
echo "REALITY_LICENSE_URL=your_verification_url" >> local.properties

# Build Debug APK
./gradlew assembleDebug
```

## Community & Support
- Website: [reality.neubofy.in](https://reality.neubofy.in)
- Email: support@neubofy.in

## License
Licensed under the GNU GPLv3. See `LICENSE` for more information.
