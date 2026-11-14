# NOTICE — 제3자 자료와 라이선스

이 저장소의 코드는 MIT 라이선스(`LICENSE`)입니다. 아래는 다른 모드/게임과의 관계입니다.

## 다른 모드의 코드/에셋은 들어 있지 않습니다
- **InSight Chests**(MrSun, 라이선스: All Rights Reserved — CurseForge 표기): 이 모드의 코드와 에셋은 jar에 포함하지 않습니다. 설치된 InSight Chests에 리플렉션과 믹신으로만 연결합니다. 컴파일을 위한 선언만 담은 가짜 클래스(stub, `com/mrsun/**`)는 빌드 때 jar에서 제외됩니다.
- **Sophisticated Core / Backpacks / Storage**(P3pp3rF1y, CurseForge 표기: All Rights Reserved), **Create**, **AE2**, **EMI**, **JEI**, **Mekanism**: 코드는 포함하지 않으며 리플렉션/믹신으로만 연결합니다. 컴파일용 stub(`net/p3pp3rf1y/**`, `dev/emi/**`)은 jar에서 제외됩니다. JEI API 일부(MIT, mezz)는 컴파일 전용으로 `libs/`에 있고 jar에는 들어가지 않습니다.

## 이 모드에 포함된 텍스처
- `iron_inner/*.png`, `sophisticated_chest_inner.png`, `backpack_inner.png`: 직접 새로 그린 이미지입니다. (Iron Chests/Sophisticated의 이미지를 복사한 것이 아닙니다.)
- `personal_steel_panel.png`: Mekanism(MIT License)의 개인 상자 모델 텍스처 일부를 가공한 것입니다. MIT 라이선스의 저작권 고지: Copyright (c) 2017-2025 Aidan C. Brady and Mekanism contributors.
- `shulker_inner/*.png`: 마인크래프트 셜커 상자 텍스처에서 안쪽 바닥을 가공한 것입니다. 마인크래프트의 에셋은 Mojang Studios의 자산이며 Minecraft EULA를 따릅니다.
- 로고/아이콘/버튼 이미지: 직접 만든 것이며, 로고 일부에 마인크래프트 상자·통 텍스처를 입체로 그려 넣었습니다.
- 그 밖의 텍스처(예: Create 금고 내부)는 파일을 포함하지 않고, 설치된 해당 모드의 텍스처를 실행 중에 참조만 합니다.

## 상표
이 프로젝트는 Mojang, Microsoft, 그리고 위에 언급된 모드의 제작자와 관련이 없는 비공식 애드온입니다.
