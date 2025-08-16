"""
Voting System Backend API
Requirements: pip install fastapi uvicorn sqlalchemy psycopg2-binary python-jose[cryptography] passlib[bcrypt] python-multipart
"""
import sqlalchemy
from fastapi import FastAPI, HTTPException, Depends, status
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import create_engine, Column, Integer, String, Text, DateTime, ForeignKey, UniqueConstraint
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session, relationship
from sqlalchemy.sql import func
from pydantic import BaseModel, EmailStr
from typing import Optional, List
from datetime import datetime, timedelta
from passlib.context import CryptContext
from jose import JWTError, jwt
import os

# Configuration
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://postgres:Postgres2020!@127.0.0.1/bobs")
SECRET_KEY = os.getenv("SECRET_KEY", "your-secret-key-change-in-production")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30

# Database setup
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = sqlalchemy.orm.declarative_base()

# Password hashing
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# OAuth2
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")


# Database Models
class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, nullable=False, index=True)
    email = Column(String(100), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime, default=func.now())
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now())

    features = relationship("Feature", back_populates="user")
    votes = relationship("Vote", back_populates="user")


class Feature(Base):
    __tablename__ = "features"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(200), nullable=False)
    description = Column(Text, nullable=False)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    vote_count = Column(Integer, default=0)
    status = Column(String(20), default="open")
    created_at = Column(DateTime, default=func.now())
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now())

    user = relationship("User", back_populates="features")
    votes = relationship("Vote", back_populates="feature")


class Vote(Base):
    __tablename__ = "votes"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    feature_id = Column(Integer, ForeignKey("features.id"), nullable=False)
    vote_type = Column(Integer, default=1)
    created_at = Column(DateTime, default=func.now())

    user = relationship("User", back_populates="votes")
    feature = relationship("Feature", back_populates="votes")

    __table_args__ = (UniqueConstraint('user_id', 'feature_id', name='_user_feature_uc'),)


# Pydantic Models
class UserCreate(BaseModel):
    username: str
    email: EmailStr
    password: str


class UserResponse(BaseModel):
    id: int
    username: str
    email: str
    created_at: datetime

    class Config:
        from_attributes = True


class FeatureCreate(BaseModel):
    title: str
    description: str


class FeatureResponse(BaseModel):
    id: int
    title: str
    description: str
    user_id: int
    username: str
    vote_count: int
    status: str
    created_at: datetime
    user_voted: bool = False

    class Config:
        from_attributes = True


class Token(BaseModel):
    access_token: str
    token_type: str


class TokenData(BaseModel):
    username: Optional[str] = None


# Initialize FastAPI app
app = FastAPI(title="Voting System API", version="1.0.0")

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Database dependency
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# Authentication functions
def verify_password(plain_password, hashed_password):
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password):
    return pwd_context.hash(password)


def authenticate_user(db: Session, username: str, password: str):
    user = db.query(User).filter(User.username == username).first()
    if not user or not verify_password(password, user.password_hash):
        return False
    return user


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=15)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt


async def get_current_user(token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise credentials_exception
        token_data = TokenData(username=username)
    except JWTError:
        raise credentials_exception
    user = db.query(User).filter(User.username == token_data.username).first()
    if user is None:
        raise credentials_exception
    return user


# API Endpoints

@app.get("/")
def read_root():
    return {"message": "Voting System API", "version": "1.0.0"}


@app.post("/register", response_model=UserResponse)
def register(user: UserCreate, db: Session = Depends(get_db)):
    # Check if user exists
    db_user = db.query(User).filter(
        (User.username == user.username) | (User.email == user.email)
    ).first()
    if db_user:
        raise HTTPException(status_code=400, detail="Username or email already registered")

    # Create new user
    hashed_password = get_password_hash(user.password)
    db_user = User(
        username=user.username,
        email=user.email,
        password_hash=hashed_password
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user


@app.post("/token", response_model=Token)
def login(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    user = authenticate_user(db, form_data.username, form_data.password)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user.username}, expires_delta=access_token_expires
    )
    return {"access_token": access_token, "token_type": "bearer"}


@app.get("/me", response_model=UserResponse)
def read_users_me(current_user: User = Depends(get_current_user)):
    return current_user


@app.post("/features", response_model=FeatureResponse)
def create_feature(
        feature: FeatureCreate,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db)
):
    db_feature = Feature(
        title=feature.title,
        description=feature.description,
        user_id=current_user.id
    )
    db.add(db_feature)
    db.commit()
    db.refresh(db_feature)

    # Add username to response
    response = FeatureResponse(
        id=db_feature.id,
        title=db_feature.title,
        description=db_feature.description,
        user_id=db_feature.user_id,
        username=current_user.username,
        vote_count=db_feature.vote_count,
        status=db_feature.status,
        created_at=db_feature.created_at,
        user_voted=False
    )
    return response


@app.get("/features", response_model=List[FeatureResponse])
def get_features(
        skip: int = 0,
        limit: int = 100,
        sort_by: str = "votes",
        db: Session = Depends(get_db)
):
    # Try to get current user (optional for public viewing)
    try:
        token = oauth2_scheme._auto_error
        current_user = get_current_user(token, db)
    except:
        current_user = None

    # Query features
    query = db.query(Feature)

    if sort_by == "votes":
        query = query.order_by(Feature.vote_count.desc())
    else:
        query = query.order_by(Feature.created_at.desc())

    features = query.offset(skip).limit(limit).all()

    # Format response
    response = []
    for feature in features:
        user_voted = False
        if current_user:
            vote = db.query(Vote).filter(
                Vote.user_id == current_user.id,
                Vote.feature_id == feature.id
            ).first()
            user_voted = vote is not None

        response.append(FeatureResponse(
            id=feature.id,
            title=feature.title,
            description=feature.description,
            user_id=feature.user_id,
            username=feature.user.username,
            vote_count=feature.vote_count,
            status=feature.status,
            created_at=feature.created_at,
            user_voted=user_voted
        ))

    return response


@app.post("/features/{feature_id}/vote")
def vote_feature(
        feature_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db)
):
    # Check if feature exists
    feature = db.query(Feature).filter(Feature.id == feature_id).first()
    if not feature:
        raise HTTPException(status_code=404, detail="Feature not found")

    # Check if user already voted
    existing_vote = db.query(Vote).filter(
        Vote.user_id == current_user.id,
        Vote.feature_id == feature_id
    ).first()

    if existing_vote:
        # Remove vote (toggle)
        db.delete(existing_vote)
        db.commit()
        return {"message": "Vote removed", "voted": False}
    else:
        # Add vote
        vote = Vote(
            user_id=current_user.id,
            feature_id=feature_id,
            vote_type=1
        )
        db.add(vote)
        db.commit()
        return {"message": "Vote added", "voted": True}


@app.delete("/features/{feature_id}")
def delete_feature(
        feature_id: int,
        current_user: User = Depends(get_current_user),
        db: Session = Depends(get_db)
):
    # Check if feature exists and belongs to user
    feature = db.query(Feature).filter(
        Feature.id == feature_id,
        Feature.user_id == current_user.id
    ).first()

    if not feature:
        raise HTTPException(status_code=404, detail="Feature not found or you don't have permission")

    db.delete(feature)
    db.commit()
    return {"message": "Feature deleted successfully"}


if __name__ == "__main__":
    import uvicorn

    # Create tables
    Base.metadata.create_all(bind=engine)
    # Run server
    uvicorn.run(app, host="0.0.0.0", port=8000)