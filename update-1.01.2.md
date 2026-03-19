# SandAC Update Log (v1.01.2)

[English](#english) | [Русский](#русский)

-----

## English

### Recent Changes:

  * **KillAura Intelligence Update:**
      * Integrated new detection patterns based on specialized datasets.
      * **Added v15:** Targeted detection for high-acceleration rotations and specific aim-error signatures.
      * **Added v16:** New logic to detect smooth, AI-based combat rotations by analyzing low deviation combined with specific speed variance.
      * **Added v17:** Detection for extreme yaw/pitch snaps and significant rotation deltas.
  * **Enhanced Scoring System:** Refined the KillAura scoring algorithm to reduce false positives while maintaining high sensitivity for multi-pattern matches.
  * **Mathematical Precision:** Improved accuracy of standard deviation and speed variance calculations for more reliable combat analysis.
  * **Code Maintenance:** Continued the policy of removing all comments from source files to ensure a lean and professional production codebase.

-----

## Русский

### Последние изменения:

  * **Обновление интеллекта KillAura:**
      * Интегрированы новые паттерны детекта на основе специализированных датасетов.
      * **Добавлен v15:** Таргетированный детект ротаций с высоким ускорением и специфическими сигнатурами ошибок прицеливания.
      * **Добавлен v16** Новая логика для обнаружения плавных AI-аур через анализ низкого отклонения в сочетании с вариативностью скорости. (Beta Test)
      * **Добавлен v17:** Детект резких рывков (snaps) по осям Yaw/Pitch и значительных дельт вращения.
  * **Улучшенная система скоринга:** Доработан алгоритм начисления баллов KillAura для минимизации ложных срабатываний при сохранении высокой чувствительности к комбинациям паттернов.
  * **Математическая точность:** Повышена точность вычислений стандартного отклонения и дисперсии скорости для более надежного анализа боя.
  * **Обслуживание кода:** Продолжена политика удаления всех комментариев из исходных файлов для обеспечения чистоты и профессионального вида кодовой базы.