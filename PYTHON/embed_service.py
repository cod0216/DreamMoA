# embed_service.py
from fastapi import FastAPI
from pydantic import BaseModel
from transformers import AutoTokenizer, AutoModel
import torch
import uvicorn
import numpy as np
from fastapi.middleware.cors import CORSMiddleware
# from api.focus import router as focus_router 

# FastAPI 애플리케이션 생성
app = FastAPI()

# BM-K/KoSimCSE-roberta 모델과 토크나이저 로드
tokenizer = AutoTokenizer.from_pretrained("BM-K/KoSimCSE-roberta")
model = AutoModel.from_pretrained("BM-K/KoSimCSE-roberta")


def mean_pooling(model_output, attention_mask):
    """
    평균 풀링을 통해 토큰 임베딩들을 하나의 문장 임베딩으로 합칩니다.
    :param model_output: 모델의 출력 (last_hidden_state 포함)
    :param attention_mask: 입력 토큰의 어텐션 마스크
    :return: 문장 임베딩 텐서
    """
    token_embeddings = model_output.last_hidden_state  # [batch_size, sequence_length, hidden_size]
    input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, dim=1)
    sum_mask = torch.clamp(input_mask_expanded.sum(dim=1), min=1e-9)
    return sum_embeddings / sum_mask


# 요청 데이터 구조 정의
class EmbedRequest(BaseModel):
    text: str


# POST /embed 엔드포인트: 입력 텍스트를 임베딩 벡터로 변환하여 반환
@app.post("/embed")
async def embed_text(request: EmbedRequest):
    # 입력 텍스트 토큰화 (truncation과 padding 옵션 포함)
    inputs = tokenizer(request.text, return_tensors="pt", truncation=True, padding=True)

    # 모델 추론 (torch.no_grad()를 사용하여 그라디언트 계산 방지)
    with torch.no_grad():
        model_output = model(**inputs)

    # 평균 풀링을 통해 문장 임베딩 추출
    sentence_embedding = mean_pooling(model_output, inputs['attention_mask'])

    # 첫 번째(유일한) 문장에 대한 임베딩을 numpy array로 변환
    embedding = sentence_embedding[0].numpy()

    # L2 정규화: 벡터의 L2 노름으로 나누어 정규화
    norm = np.linalg.norm(embedding)
    if norm > 0:
        embedding = embedding / norm

    return {"embedding": embedding.tolist()}


# 로컬 개발 시 uvicorn으로 실행 (실제 배포는 Docker 등으로 진행)
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
