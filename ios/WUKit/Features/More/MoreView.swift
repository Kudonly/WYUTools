import SwiftUI

struct MoreView: View {
    private let columns = Array(
        repeating: GridItem(.flexible(), spacing: 16),
        count: 4
    )

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("更多")
                    .font(.system(size: 32, weight: .bold))

                LazyVGrid(columns: columns, spacing: 20) {
                    ForEach(WKTool.allCases) { tool in
                        NavigationLink(value: tool) {
                            VStack(spacing: 8) {
                                ToolBadge(tool: tool, size: 54)
                                Text(tool.title)
                                    .font(.system(size: 12))
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                    }
                }

                Color.clear.frame(height: WK.bottomBarInset)
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 8)
        }
        .background(PageBackground())
        .scrollIndicators(.hidden)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: WKTool.self) { tool in
            ToolDestination(tool: tool)
        }
    }
}

#Preview {
    NavigationStack {
        MoreView()
    }
}
