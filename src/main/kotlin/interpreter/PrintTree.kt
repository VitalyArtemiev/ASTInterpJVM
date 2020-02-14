package interpreter


fun ASTNode.printTree(): String {
    return ""
}

fun Prog.printTree(): String {
    var s = ""
    for (n in nodes) {
        s += n.printTree()
    }
    return(" ".repeat(s.length / 2) + "Prog\n" + s)
}