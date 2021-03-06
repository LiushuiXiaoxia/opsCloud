package com.baiyi.opscloud.factory.ticket.impl.subscribe;

import com.baiyi.opscloud.common.base.TicketPhase;
import com.baiyi.opscloud.common.base.TicketSubscribeType;
import com.baiyi.opscloud.decorator.DepartmentMemberDecorator;
import com.baiyi.opscloud.domain.BusinessWrapper;
import com.baiyi.opscloud.domain.generator.opscloud.OcUser;
import com.baiyi.opscloud.domain.generator.opscloud.OcWorkorder;
import com.baiyi.opscloud.domain.generator.opscloud.OcWorkorderTicket;
import com.baiyi.opscloud.domain.generator.opscloud.OcWorkorderTicketSubscribe;
import com.baiyi.opscloud.domain.vo.org.OcOrgDepartmentMemberVO;
import com.baiyi.opscloud.domain.vo.org.OrgApprovalVO;
import com.baiyi.opscloud.domain.vo.workorder.ApprovalStepsVO;
import com.baiyi.opscloud.domain.vo.workorder.OcWorkorderTicketVO;
import com.baiyi.opscloud.factory.ticket.ITicketSubscribe;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.stream.Collectors;

/**
 * @Author baiyi
 * @Date 2020/5/6 1:41 下午
 * @Version 1.0
 */
@Slf4j
@Component
public class TicketOrgApprovalSubscribe extends BaseTicketSubscribe implements ITicketSubscribe {

    @Resource
    private DepartmentMemberDecorator departmentMemberDecorator;

    @Override
    public String getKey() {
        return TicketPhase.ORG_APPROVAL.getPhase();
    }

    @Override
    public OcWorkorderTicketSubscribe queryTicketSubscribe(OcWorkorderTicket ocWorkorderTicket, OcUser ocUser) {
        OcWorkorder ocWorkorder = getOcWorkorderById(ocWorkorderTicket.getWorkorderId());
        if (!ocWorkorder.getOrgApproval()) return null;
        return ocWorkorderTicketSubscribeService.queryOcWorkorderTicketSubscribeByParam(ocWorkorderTicket.getId(), ocUser.getId(), TicketSubscribeType.ORG_APPROVAL.getType());
    }

    // 工单创建状态-进入审批阶段
    @Override
    public BusinessWrapper<Boolean> subscribe(OcWorkorderTicket ocWorkorderTicket) {
        OrgApprovalVO.OrgApproval orgApproval = departmentMemberDecorator.decorator(ocWorkorderTicket.getUserId());
        if (orgApproval.getPreferenceDeptMember() != null) {
            addTicketSubscribe(ocWorkorderTicket, orgApproval.getPreferenceDeptMember().getUserId(), TicketSubscribeType.ORG_APPROVAL.getType());
        }
        if (orgApproval.getAlternativeDeptMembers() != null) {
            for (OcOrgDepartmentMemberVO.DepartmentMember member : orgApproval.getAlternativeDeptMembers())
                addTicketSubscribe(ocWorkorderTicket, member.getUserId(), TicketSubscribeType.ORG_APPROVAL.getType());
        }
        return BusinessWrapper.SUCCESS;

    }

    @Override
    public void invokeFlowStep(OcWorkorderTicketVO.Ticket ticket,String ticketPhase) {
        OrgApprovalVO.OrgApproval orgApproval = departmentMemberDecorator.decorator(ticket.getUserId());
        ApprovalStepsVO.ApprovalStep approvalStep = ApprovalStepsVO.ApprovalStep.builder()
                .title("上级审批")
                .description(getDescription(orgApproval))
                .build();
        ticket.getApprovalDetail().getApprovalSteps().add(approvalStep);

        if(TicketPhase.ORG_APPROVAL.getPhase().equals(ticketPhase))
            ticket.getApprovalDetail().setActive(ticket.getApprovalDetail().getApprovalSteps().size());
    }

    private String getDescription(OrgApprovalVO.OrgApproval orgApproval) {
        if (orgApproval.getIsError())
            return orgApproval.getErrorMsg();
        if (orgApproval.getIsApprovalAuthority())
            return "本人拥有审批权";
        if (orgApproval.getPreferenceDeptMember() != null)
            return orgApproval.getPreferenceDeptMember().getDisplayName();
        return Joiner.on(",").join(orgApproval.getAlternativeDeptMembers().stream().map(e -> e.getDisplayName()).collect(Collectors.toList()));
    }

}
