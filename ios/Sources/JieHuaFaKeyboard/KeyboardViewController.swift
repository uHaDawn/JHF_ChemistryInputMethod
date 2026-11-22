import UIKit

/// 阶段 0 的键盘扩展：只验证「系统能添加并呼出这个键盘、能向宿主输入框写字符」。
/// 几何与视觉还原在阶段 3 才接入，这里刻意保持最小。
final class KeyboardViewController: UIInputViewController {

    private let titleLabel = UILabel()
    private let testButton = UIButton(type: .system)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGray5

        titleLabel.text = "接化发键盘（阶段 0）"
        titleLabel.font = .systemFont(ofSize: 15, weight: .medium)
        titleLabel.textAlignment = .center
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        testButton.setTitle("输入 jhf", for: .normal)
        testButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        testButton.backgroundColor = .white
        testButton.layer.cornerRadius = 8
        testButton.translatesAutoresizingMaskIntoConstraints = false
        testButton.addTarget(self, action: #selector(insertTest), for: .touchUpInside)

        view.addSubview(titleLabel)
        view.addSubview(testButton)

        NSLayoutConstraint.activate([
            view.heightAnchor.constraint(equalToConstant: 260),
            titleLabel.topAnchor.constraint(equalTo: view.topAnchor, constant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            testButton.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 20),
            testButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            testButton.widthAnchor.constraint(equalToConstant: 160),
            testButton.heightAnchor.constraint(equalToConstant: 44),
        ])
    }

    @objc private func insertTest() {
        textDocumentProxy.insertText("jhf")
    }
}
