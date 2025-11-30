# iOS App Setup

This directory should contain the iOS Xcode project for the Kotlin Multiplatform Sudoku app.

## Setup Instructions

1. Create a new Xcode project in this directory:
   - File > New > Project
   - Choose "App" template
   - Interface: SwiftUI
   - Name: NiceSudoku
   - Team: None (or your team)
   - Organization Identifier: com.nicesudoku
   - Bundle Identifier: com.nicesudoku.ios

2. Add Kotlin/Native framework:
   - The shared module will build a framework for iOS
   - Add the framework to the Xcode project
   - Link it in Build Phases > Link Binary With Libraries

3. Create SwiftUI views that use the shared Kotlin code:
   - Import the Kotlin framework
   - Create SwiftUI wrappers around the Compose UI (or use SwiftUI directly)
   - Connect to the GameViewModel from the shared module

4. Configure the iOS target in the shared build.gradle.kts (already done)

## File Structure (to be created)

```
ios/
├── NiceSudoku.xcodeproj/
├── NiceSudoku/
│   ├── App.swift
│   ├── ContentView.swift
│   ├── GameView.swift
│   └── ...
└── README.md (this file)
```

## Integration with Kotlin

The iOS app will use the shared Kotlin code through:
- `GameViewModel` for business logic
- SwiftUI for the native iOS UI
- The shared UI components can be adapted or recreated in SwiftUI

Note: Full iOS setup requires Xcode and macOS environment.


