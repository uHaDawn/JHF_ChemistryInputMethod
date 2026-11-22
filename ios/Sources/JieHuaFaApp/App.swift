import SwiftUI
import JieHuaFaCore

@main
struct JieHuaFaApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        VStack(spacing: 14) {
            Text("接化发 · 化学输入法")
                .font(.system(size: 24, weight: .semibold))
            Text("iOS 阶段 0 验证包")
                .font(.system(size: 15))
                .foregroundStyle(.secondary)
            Text(CoreInfo.stage)
                .font(.system(size: 13))
                .foregroundStyle(.tertiary)
                .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
    }
}
