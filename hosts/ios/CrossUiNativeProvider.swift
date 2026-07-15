import Foundation

@_silgen_name("crossui_initial_document") private func crossui_initial_document() -> UnsafeMutablePointer<CChar>?
@_silgen_name("crossui_dispatch_event") private func crossui_dispatch_event(_ event: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
@_silgen_name("crossui_string_free") private func crossui_string_free(_ value: UnsafeMutablePointer<CChar>?)

final class CrossUiNativeProvider: CrossUiDocumentProvider {
    func initialDocument() throws -> String {
        try read(crossui_initial_document())
    }

    func dispatchEvent(_ event: String) throws -> String {
        try event.withCString { try read(crossui_dispatch_event($0)) }
    }

    private func read(_ pointer: UnsafeMutablePointer<CChar>?) throws -> String {
        guard let pointer else { throw CrossUiNativeError.emptyResponse }
        defer { crossui_string_free(pointer) }
        return String(cString: pointer)
    }
}

private enum CrossUiNativeError: Error { case emptyResponse }
