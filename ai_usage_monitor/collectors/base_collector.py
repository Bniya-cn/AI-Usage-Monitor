"""
base_collector.py - 采集器抽象基类
所有服务采集器继承此类并实现 collect() 方法
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class UsageRecord:
    """单条用量记录"""
    service: str
    model: Optional[str]
    date: str                    # YYYY-MM-DD
    tokens_in: int = 0
    tokens_out: int = 0
    requests: int = 0
    cost_usd: float = 0.0
    extra: dict = field(default_factory=dict)


class BaseCollector(ABC):
    """采集器基类"""

    service_id: str = ""         # 子类必须定义
    display_name: str = ""

    @abstractmethod
    def collect(self) -> list[UsageRecord]:
        """
        执行数据采集，返回 UsageRecord 列表。
        子类实现中如遇错误，应抛出异常并附带友好的中文错误信息。
        """
        ...

    def is_available(self) -> bool:
        """检查该采集器是否具备运行所需凭证/条件"""
        return True
