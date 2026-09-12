package com.codeatlas.code.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目目录结构节点。
 */
public class StructureNode {

    private String name;

    private String path;

    private boolean directory;

    private List<StructureNode> children = new ArrayList<>();

    public static StructureNode directory(String name, String path) {
        StructureNode node = new StructureNode();
        node.setName(name);
        node.setPath(path);
        node.setDirectory(true);
        return node;
    }

    public static StructureNode file(String name, String path) {
        StructureNode node = new StructureNode();
        node.setName(name);
        node.setPath(path);
        node.setDirectory(false);
        node.setChildren(null);
        return node;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public List<StructureNode> getChildren() {
        return children;
    }

    public void setChildren(List<StructureNode> children) {
        this.children = children;
    }
}
