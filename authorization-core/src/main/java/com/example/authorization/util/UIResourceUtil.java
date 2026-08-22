package com.example.authorization.util;

import java.util.Objects;

public class UIResourceUtil {
    public static boolean matches (String actual, String expected) {
        return Objects.equals(actual, expected);
    }
}
