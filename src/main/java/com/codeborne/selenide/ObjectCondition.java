package com.codeborne.selenide;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ObjectCondition<T> {
  @Nonnull
  @CheckReturnValue
  String description();

  @Nonnull
  @CheckReturnValue
  String negativeDescription();

  @CheckReturnValue
  boolean test(T object);

  @Nullable
  @CheckReturnValue
  Object actualValue(T object);
}