package org.checkerframework.checker.dividebyzero.qual;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import org.checkerframework.framework.qual.SubtypeOf;

// mostly used to simplify the some transfer functions
// and be more precise than my initial abstract representation in ex 29
@SubtypeOf({Top.class})
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface NonZero { }