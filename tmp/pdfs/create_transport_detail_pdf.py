from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    PageBreak,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "output" / "pdf" / "KoreaPoint_transport_detail_plan.pdf"
FONT = Path("C:/Windows/Fonts/malgun.ttf")
BOLD_FONT = Path("C:/Windows/Fonts/malgunbd.ttf")


def register_fonts():
    pdfmetrics.registerFont(TTFont("Malgun", str(FONT)))
    pdfmetrics.registerFont(TTFont("MalgunBold", str(BOLD_FONT)))


def bullet(text):
    return Paragraph(f"<font name='MalgunBold'>-</font> {text}", STYLES["Body"])


def heading(text):
    return Paragraph(text, STYLES["Section"])


def subheading(text):
    return Paragraph(text, STYLES["Subsection"])


def body(text):
    return Paragraph(text, STYLES["Body"])


def build_pdf():
    doc = SimpleDocTemplate(
        str(OUT),
        pagesize=A4,
        rightMargin=18 * mm,
        leftMargin=18 * mm,
        topMargin=18 * mm,
        bottomMargin=18 * mm,
        title="KoreaPoint 교통 상세 페이지 유지보수 및 추가 개발 계획",
    )

    story = []

    story.append(Paragraph("KoreaPoint 추가 개발 문서", STYLES["Kicker"]))
    story.append(Paragraph("교통 상세 페이지 유지보수 및 추가 개발 계획", STYLES["DocTitle"]))
    story.append(Spacer(1, 5 * mm))

    overview = (
        "교통 상세 페이지는 교통 검색 결과에서 사용자가 선택한 경로를 기반으로 실제 이동에 필요한 "
        "상세 정보를 제공하는 화면이다. 사용자가 환승이 적은 경로, 도보가 적은 경로, 혼잡도가 낮은 "
        "경로 등 검색 결과 중 하나를 선택하면 해당 경로의 지하철, 버스, 도보 이동 단계와 지도 기반 "
        "경로 안내, 실시간 도착 정보, 혼잡도 정보를 확인할 수 있도록 구성되어 있다."
    )
    story.append(body(overview))
    story.append(Spacer(1, 6 * mm))

    summary_table = Table(
        [
            ["담당 기능", "교통 검색 결과에서 선택한 단일 경로의 상세 이동 안내 제공"],
            ["주요 화면", "교통 상세 페이지"],
            ["주요 정보", "이동 단계, 지도 경로, 실시간 도착 정보, 혼잡도 안내"],
            ["개선 방향", "배포 안정성 강화, 예외 처리 보완, 이동 중 안내 기능 고도화"],
        ],
        colWidths=[31 * mm, 128 * mm],
    )
    summary_table.setStyle(
        TableStyle(
            [
                ("FONTNAME", (0, 0), (-1, -1), "Malgun"),
                ("FONTNAME", (0, 0), (0, -1), "MalgunBold"),
                ("FONTSIZE", (0, 0), (-1, -1), 9),
                ("TEXTCOLOR", (0, 0), (0, -1), colors.HexColor("#1f4e79")),
                ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#edf4fb")),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#c9d6e2")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 7),
                ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ]
        )
    )
    story.append(summary_table)
    story.append(Spacer(1, 8 * mm))

    story.append(heading("1. 유지보수 내용"))
    story.append(subheading("1.1 혼잡도 예측 모델 구조 개선"))
    story.append(
        body(
            "현재 교통 상세 페이지의 혼잡도 예측 기능은 FastAPI 서버와 연동하여 결과를 받아오는 "
            "구조이므로, 별도 서버 실행 여부에 따라 기능이 동작하지 않을 수 있다. 배포 안정성을 "
            "높이기 위해 FastAPI로 분리되어 있던 혼잡도 예측 모델을 ONNX 형식으로 변환하고, "
            "Spring Boot 서버 내부에서 직접 추론하도록 개선할 필요가 있다. 이를 통해 외부 예측 "
            "서버 의존성을 줄이고 서비스 실행 구조를 단순화할 수 있다."
        )
    )
    story.append(Spacer(1, 3 * mm))

    story.append(subheading("1.2 선택 경로 상세 데이터 유지 관리"))
    story.append(
        body(
            "교통 검색 결과에서 사용자가 선택한 경로는 routeId를 기준으로 상세 페이지에 전달된다. "
            "따라서 선택한 경로의 지하철, 버스, 도보 이동 단계가 상세 페이지에서 누락되지 않도록 "
            "데이터 전달 구조를 유지보수할 필요가 있다."
        )
    )
    story.append(Spacer(1, 3 * mm))

    story.append(subheading("1.3 실시간 도착 정보 예외 처리 보완"))
    story.append(
        body(
            "교통 상세 페이지에서는 지하철 실시간 도착 정보를 제공하므로, 서울시 API 응답 실패나 "
            "역명 검색 실패 상황에서도 화면이 깨지지 않도록 예외 처리와 안내 문구를 보완할 필요가 있다."
        )
    )
    story.append(Spacer(1, 3 * mm))

    story.append(subheading("1.4 지도 기반 경로 안내 안정화"))
    story.append(
        body(
            "카카오맵을 통해 출발지, 목적지, 환승 지점, 이동 경로를 시각적으로 제공하므로 지도 API "
            "호출 오류, 좌표 누락, 위치 권한 거부 상황에 대한 처리가 필요하다."
        )
    )
    story.append(Spacer(1, 8 * mm))

    story.append(heading("2. 추가 개발 아이디어"))
    story.append(subheading("2.1 ONNX 기반 혼잡도 예측 통합"))
    story.append(
        body(
            "혼잡도 예측 모델을 ONNX 기반으로 통합하여 교통 상세 페이지에서 실시간으로 혼잡도 예측 "
            "결과를 제공하도록 개선한다. 기존 FastAPI 연동 방식보다 배포가 간단하고 서버 간 통신 "
            "실패 가능성을 줄일 수 있어 공모전 시연 환경에서도 안정적인 동작이 가능하다."
        )
    )
    story.append(PageBreak())

    story.append(subheading("2.2 경로 유형 표시 기능 추가"))
    story.append(
        body(
            "사용자가 앞 페이지에서 선택한 경로가 환승이 적은 경로인지, 도보가 적은 경로인지, "
            "혼잡도가 낮은 경로인지 상세 페이지 상단에 표시한다. 이를 통해 사용자가 자신이 선택한 "
            "경로의 특징을 상세 화면에서도 바로 이해할 수 있다."
        )
    )
    story.append(Spacer(1, 3 * mm))

    story.append(subheading("2.3 환승 안내 상세화"))
    story.append(
        body(
            "현재 이동 단계 안내를 기반으로 환승역, 환승 노선, 이동 방향, 예상 환승 소요 시간 등을 "
            "추가하여 관광객이 실제 이동 중에도 쉽게 따라갈 수 있도록 개선한다."
        )
    )
    story.append(Spacer(1, 3 * mm))

    story.append(subheading("2.4 도착 알림 기능 고도화"))
    story.append(
        body(
            "현재 위치 정보를 활용하여 목적지 근처에 도착했을 때 안내를 제공하는 기능을 개선하고, "
            "다음 이동 단계나 하차 지점에 가까워졌을 때 알림을 제공하는 방식으로 확장할 수 있다."
        )
    )

    doc.build(story)


register_fonts()
STYLES = getSampleStyleSheet()
STYLES.add(
    ParagraphStyle(
        name="Kicker",
        fontName="MalgunBold",
        fontSize=9,
        leading=12,
        textColor=colors.HexColor("#5c6f82"),
        spaceAfter=4,
    )
)
STYLES.add(
    ParagraphStyle(
        name="DocTitle",
        fontName="MalgunBold",
        fontSize=18,
        leading=24,
        textColor=colors.HexColor("#16324f"),
        spaceAfter=8,
    )
)
STYLES.add(
    ParagraphStyle(
        name="Section",
        fontName="MalgunBold",
        fontSize=13,
        leading=18,
        textColor=colors.HexColor("#1f4e79"),
        spaceBefore=4,
        spaceAfter=5,
    )
)
STYLES.add(
    ParagraphStyle(
        name="Subsection",
        fontName="MalgunBold",
        fontSize=10.5,
        leading=15,
        textColor=colors.HexColor("#222222"),
        spaceBefore=3,
        spaceAfter=3,
    )
)
STYLES.add(
    ParagraphStyle(
        name="Body",
        fontName="Malgun",
        fontSize=9.4,
        leading=15.2,
        textColor=colors.HexColor("#30343b"),
        wordWrap="CJK",
        spaceAfter=2,
    )
)

if __name__ == "__main__":
    build_pdf()
    print(OUT)
